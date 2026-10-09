package com.rodrigomachado.riff.feature.gameplay

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.audio.LocalSong
import com.rodrigomachado.riff.core.audio.MusicStartFinder
import com.rodrigomachado.riff.core.audio.SfxPlayer
import com.rodrigomachado.riff.core.audio.SfxPlayerFactory
import com.rodrigomachado.riff.core.audio.SongPlayer
import com.rodrigomachado.riff.core.audio.SongPlayerFactory
import com.rodrigomachado.riff.core.audio.SpotifySong
import com.rodrigomachado.riff.core.audio.StemSongPlayerFactory
import com.rodrigomachado.riff.core.data.ChartOffsetRepository
import com.rodrigomachado.riff.core.data.MAX_SONG_OFFSET_MS
import com.rodrigomachado.riff.core.data.ScoreRepository
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.TrackMatchRepository
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.game.ChartAudio
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

enum class UnavailableReason { NO_AUDIO, CHART_MISSING, FOLDER_ACCESS_LOST, AUDIO_FAILED, SPOTIFY_UNAVAILABLE }

sealed interface GameplayUiState {
    data object Loading : GameplayUiState

    data class Ready(
        val title: String,
        val artist: String,
        val difficulty: Difficulty,
        val session: GameSession,
        val settings: Settings,
        val isPaused: Boolean = false,
        val withoutSeparateGuitar: Boolean = false,
        val songOffsetMs: Int = 0,
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
    private val chartOffsets: ChartOffsetRepository,
    private val trackMatches: TrackMatchRepository,
    musicStarts: MusicStartFinder,
    @LocalSong private val songPlayers: SongPlayerFactory,
    @SpotifySong private val spotifyPlayers: SongPlayerFactory,
    private val stemPlayers: StemSongPlayerFactory,
    private val sfxPlayers: SfxPlayerFactory,
    private val timeSource: TimeSource,
) : ViewModel() {
    private val chartId: String = checkNotNull(savedStateHandle["chartId"])
    private val difficulty: Difficulty = Difficulty.valueOf(checkNotNull(savedStateHandle["difficulty"]))
    private val spotifyTrackUri: String? = savedStateHandle["spotifyTrackUri"]
    private val trackOffsetMs: Int = savedStateHandle["trackOffsetMs"] ?: 0
    private val spotifyOffsets = SpotifySongOffset(trackMatches, musicStarts)
    private val state = MutableStateFlow<GameplayUiState>(GameplayUiState.Loading)
    val uiState: StateFlow<GameplayUiState> = state.asStateFlow()

    private var player: SongPlayer? = null
    private var sfx: SfxPlayer? = null
    private var finishing = false
    private var songOffsetMs = 0

    // O app pode ir para segundo plano enquanto a música carrega: a partida começa pausada.
    private var pauseRequested = false

    init {
        viewModelScope.launch { state.value = load() }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // cada falha vira uma explicação na tela
    private suspend fun load(): GameplayUiState = try {
        val playable = loader.load(chartId, difficulty)
        val settings = settingsRepository.settings.first()
        val (songPlayer, withoutSeparateGuitar) = prepareSong(playable.audio)
        songPlayer.setVolume(settings.masterVolume)
        val effects = sfxPlayers.create().also {
            sfx = it
            it.setVolumes(settings.effectiveHitVolume, settings.effectiveMissVolume)
        }
        songOffsetMs = spotifyTrackUri?.let { spotifyOffsets.forTrack(it, chartId, trackOffsetMs, playable.audio) }
            ?: chartOffsets.get(chartId)
        val offset = settings.audioOffsetMs.toLong() + songOffsetMs
        val session = GameSession(
            playable.notes,
            songPlayer,
            effects,
            timeSource,
            offset,
            hitSounds = spotifyTrackUri == null,
            // Na biblioteca o jogador veio jogar: volta um pouco para retomar. No Spotify veio ouvir: segue.
            rewindOnResumeMs = if (spotifyTrackUri == null) LIBRARY_REWIND_MS else 0,
        )
        if (!pauseRequested) session.start()
        GameplayUiState.Ready(
            playable.chart.title,
            playable.chart.artist,
            difficulty,
            session,
            settings,
            pauseRequested,
            withoutSeparateGuitar,
            songOffsetMs = songOffsetMs,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        release()
        GameplayUiState.Unavailable(reasonFor(e))
    }

    /** Spotify, faixas separadas ou um arquivo só; se as faixas falharem, cai no arquivo só (com aviso). */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // a falha das faixas vira o modo de um arquivo
    private suspend fun prepareSong(audio: ChartAudio?): Pair<SongPlayer, Boolean> {
        spotifyTrackUri?.let { return prepared(spotifyPlayers.create(), it, ::SpotifyFailedException) to false }
        val chartAudio = audio ?: throw NoAudioException()
        if (chartAudio !is ChartAudio.Stems) {
            return prepared(songPlayers.create(), chartAudio.singleUri, ::AudioFailedException) to false
        }
        val stems = stemPlayers.create(chartAudio.guitarUri, chartAudio.backingUris).also { player = it }
        try {
            stems.prepare(chartAudio.guitarUri)
            return stems to false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            stems.release()
            player = null
        }
        return prepared(songPlayers.create(), chartAudio.singleUri, ::AudioFailedException) to true
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun prepared(created: SongPlayer, uri: String, wrap: (Throwable) -> Exception): SongPlayer {
        player = created
        try {
            created.prepare(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw wrap(e)
        }
        return created
    }

    private fun reasonFor(error: Exception): UnavailableReason = when (error) {
        is NoAudioException -> UnavailableReason.NO_AUDIO
        is AudioFailedException -> UnavailableReason.AUDIO_FAILED
        is SpotifyFailedException -> UnavailableReason.SPOTIFY_UNAVAILABLE
        is FolderAccessLostException -> UnavailableReason.FOLDER_ACCESS_LOST
        else -> UnavailableReason.CHART_MISSING
    }

    private class NoAudioException : Exception("pasta sem áudio")

    private class AudioFailedException(cause: Throwable) : Exception("áudio não pôde ser preparado", cause)

    private class SpotifyFailedException(cause: Throwable) : Exception("Spotify não pôde tocar", cause)

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

    /** A música pausou ou voltou fora do jogo (notificação do Spotify, conexão caiu e voltou). */
    fun onPlaybackChanged(paused: Boolean) {
        if (player == null) return
        val ready = state.value as? GameplayUiState.Ready ?: return
        if (ready.isPaused == paused) return
        if (paused) ready.session.pause()
        state.update { ready.copy(isPaused = paused) }
    }

    /** Ajuste fino na pausa: vale ao continuar e fica salvo para esta música (Spotify) ou este mapa. */
    fun adjustSongOffset(deltaMs: Int) {
        val ready = state.value as? GameplayUiState.Ready ?: return
        val updated = (songOffsetMs + deltaMs).coerceIn(-MAX_SONG_OFFSET_MS, MAX_SONG_OFFSET_MS)
        if (updated == songOffsetMs) return
        ready.session.adjustOffset((updated - songOffsetMs).toLong())
        songOffsetMs = updated
        state.update { ready.copy(songOffsetMs = updated) }
        viewModelScope.launch {
            val uri = spotifyTrackUri
            if (uri != null) trackMatches.saveOffset(uri, chartId, updated) else chartOffsets.save(chartId, updated)
        }
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

/** Quanto a biblioteca volta ao continuar depois de uma pausa. */
private const val LIBRARY_REWIND_MS = 500L
