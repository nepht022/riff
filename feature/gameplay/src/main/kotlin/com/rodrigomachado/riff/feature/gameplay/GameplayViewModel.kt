package com.rodrigomachado.riff.feature.gameplay

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.audio.SfxPlayer
import com.rodrigomachado.riff.core.audio.SfxPlayerFactory
import com.rodrigomachado.riff.core.audio.SongPlayer
import com.rodrigomachado.riff.core.audio.SongPlayerFactory
import com.rodrigomachado.riff.core.data.ScoreRepository
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.game.ChartTrackLoader
import com.rodrigomachado.riff.core.game.TimeSource
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.model.ScoreRecord
import com.rodrigomachado.riff.core.model.Settings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class UnavailableReason { NO_AUDIO, CHART_MISSING, FOLDER_ACCESS_LOST, AUDIO_FAILED }

sealed interface GameplayUiState {
    data object Loading : GameplayUiState

    data class Ready(
        val title: String,
        val artist: String,
        val difficulty: Difficulty,
        val session: GameSession,
        val settings: Settings,
        val isPaused: Boolean = false,
    ) : GameplayUiState

    data class Unavailable(val reason: UnavailableReason) : GameplayUiState

    data class Finished(val scoreId: Long) : GameplayUiState
}

@HiltViewModel
class GameplayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loader: ChartTrackLoader,
    private val settingsRepository: SettingsRepository,
    private val scores: ScoreRepository,
    private val songPlayers: SongPlayerFactory,
    private val sfxPlayers: SfxPlayerFactory,
    private val timeSource: TimeSource,
) : ViewModel() {
    private val chartId: String = checkNotNull(savedStateHandle["chartId"])
    private val difficulty: Difficulty = Difficulty.valueOf(checkNotNull(savedStateHandle["difficulty"]))
    private val state = MutableStateFlow<GameplayUiState>(GameplayUiState.Loading)
    val uiState: StateFlow<GameplayUiState> = state.asStateFlow()

    private var player: SongPlayer? = null
    private var sfx: SfxPlayer? = null
    private var finishing = false

    // O app pode ir para segundo plano enquanto a música carrega: a partida começa pausada.
    private var pauseRequested = false

    init {
        viewModelScope.launch { state.value = load() }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // cada falha vira uma explicação na tela
    private suspend fun load(): GameplayUiState = try {
        val playable = loader.load(chartId, difficulty)
        val audioUri = playable.audioUri ?: throw NoAudioException()
        val settings = settingsRepository.settings.first()
        val songPlayer = songPlayers.create().also { player = it }
        try {
            songPlayer.prepare(audioUri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AudioFailedException(e)
        }
        val effects = sfxPlayers.create().also {
            sfx = it
            it.setVolume(settings.sfxVolume)
        }
        val session = GameSession(playable.notes, songPlayer, effects, timeSource, settings.audioOffsetMs.toLong())
        if (!pauseRequested) session.start()
        GameplayUiState.Ready(
            playable.chart.title,
            playable.chart.artist,
            difficulty,
            session,
            settings,
            pauseRequested,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        release()
        GameplayUiState.Unavailable(reasonFor(e))
    }

    private fun reasonFor(error: Exception): UnavailableReason = when (error) {
        is NoAudioException -> UnavailableReason.NO_AUDIO
        is AudioFailedException -> UnavailableReason.AUDIO_FAILED
        is FolderAccessLostException -> UnavailableReason.FOLDER_ACCESS_LOST
        else -> UnavailableReason.CHART_MISSING
    }

    private class NoAudioException : Exception("pasta sem áudio")

    private class AudioFailedException(cause: Throwable) : Exception("áudio não pôde ser preparado", cause)

    fun pause() {
        if (state.value is GameplayUiState.Loading) pauseRequested = true
        if (player == null) return // já liberado: saindo ou terminando
        val ready = state.value as? GameplayUiState.Ready ?: return
        ready.session.pause()
        state.update { ready.copy(isPaused = true) }
    }

    fun resume() {
        if (state.value is GameplayUiState.Loading) pauseRequested = false
        if (player == null) return
        val ready = state.value as? GameplayUiState.Ready ?: return
        ready.session.resume()
        state.update { ready.copy(isPaused = false) }
    }

    fun onSessionFinished() {
        val ready = state.value as? GameplayUiState.Ready ?: return
        if (finishing) return
        finishing = true
        release()
        val engine = ready.session.engine
        val record = ScoreRecord(
            id = 0,
            chartId = chartId,
            difficulty = difficulty,
            score = engine.score,
            accuracy = engine.accuracy(),
            maxCombo = engine.state.maxCombo,
            perfect = engine.count(Judgment.PERFECT),
            good = engine.count(Judgment.GOOD),
            miss = engine.count(Judgment.MISS),
            playedAtEpochMs = System.currentTimeMillis(),
        )
        viewModelScope.launch { state.value = GameplayUiState.Finished(scores.save(record)) }
    }

    /** Para e libera o áudio, ao terminar ou quando a tela sai de vez ([onCleared]). */
    fun release() {
        player?.release()
        sfx?.release()
        player = null
        sfx = null
    }

    override fun onCleared() = release()
}
