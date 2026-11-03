package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.audio.PlaybackInterruptions
import com.rodrigomachado.riff.core.audio.PlaybackInterruptionsFactory
import com.rodrigomachado.riff.core.audio.SfxPlayer
import com.rodrigomachado.riff.core.audio.SfxPlayerFactory
import com.rodrigomachado.riff.core.audio.SongPlayer
import com.rodrigomachado.riff.core.audio.SongPlayerFactory
import com.rodrigomachado.riff.core.audio.StemSongPlayerFactory
import com.rodrigomachado.riff.core.data.ChartOffsetRepository
import com.rodrigomachado.riff.core.data.MAX_SONG_OFFSET_MS
import com.rodrigomachado.riff.core.data.ScoreRepository
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.game.ChartAudio
import com.rodrigomachado.riff.core.data.game.ChartNotFoundException
import com.rodrigomachado.riff.core.data.game.ChartTrackLoader
import com.rodrigomachado.riff.core.data.ranking.RankingRepository
import com.rodrigomachado.riff.core.game.TimeSource
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.model.ScoreRecord
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UnavailableReason {
    NO_AUDIO,
    CHART_MISSING,
    CHART_UNREADABLE,
    FOLDER_ACCESS_LOST,
    AUDIO_FAILED,
    UNEXPECTED,
}

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
        /** Imagem do mapa atrás da pista (com a opção ligada e se o mapa tiver uma). */
        val art: ImageBitmap? = null,
        /** Pausada porque o Android negou o áudio ao continuar (no meio de uma ligação). */
        val audioBusy: Boolean = false,
    ) : GameplayUiState

    data class Unavailable(val reason: UnavailableReason) : GameplayUiState

    data class Finished(val scoreId: Long) : GameplayUiState
}

/** A partida de [chartId] em [difficulty]. No celular, o Hilt cria pela `HiltGameplayViewModel`; no PC, o app. */
open class GameplayViewModel(
    private val chartId: String,
    private val difficulty: Difficulty,
    private val loader: ChartTrackLoader,
    private val settingsRepository: SettingsRepository,
    private val scores: ScoreRepository,
    private val chartOffsets: ChartOffsetRepository,
    private val songPlayers: SongPlayerFactory,
    private val stemPlayers: StemSongPlayerFactory,
    private val sfxPlayers: SfxPlayerFactory,
    private val timeSource: TimeSource,
    private val interruptionsFactory: PlaybackInterruptionsFactory,
    private val artImages: ChartArtImages,
    private val ranking: RankingRepository,
) : ViewModel() {
    private val state = MutableStateFlow<GameplayUiState>(GameplayUiState.Loading)
    val uiState: StateFlow<GameplayUiState> = state.asStateFlow()

    private var player: SongPlayer? = null
    private var sfx: SfxPlayer? = null

    // Ligação, outro app tocando ou fone desconectado pausam a partida.
    private var interruptions: PlaybackInterruptions? = null
    private var finishing = false
    private var songOffsetMs = 0

    // O app pode ir para segundo plano enquanto a música carrega: a partida começa pausada.
    private var pauseRequested = false

    init {
        viewModelScope.launch { state.value = load() }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // cada falha vira uma explicação na tela
    private suspend fun load(): GameplayUiState = try {
        val playable = loader.read(chartId, difficulty)
        val settings = settingsRepository.settings.first()
        // Antes de a música tocar: ler a imagem leva tempo e a partida só aparece depois.
        val art = if (settings.showChartImage) artImages.loadOrNull(chartId) else null
        val (songPlayer, withoutSeparateGuitar) = prepareSong(playable.audio)
        songPlayer.setVolume(settings.masterVolume)
        val effects = sfxPlayers.create().also {
            sfx = it
            it.setVolumes(settings.effectiveHitVolume, settings.effectiveMissVolume)
        }
        songOffsetMs = chartOffsets.get(chartId)
        val offset = settings.audioOffsetMs.toLong() + songOffsetMs
        val session = GameSession(
            playable.notes,
            songPlayer,
            effects,
            timeSource,
            offset,
            // Sem som de acerto: sem guitarra separada a música toca inteira; com ela, a guitarra é a resposta.
            hitSounds = false,
            // O jogador veio jogar: volta um pouco para retomar no ritmo.
            rewindOnResumeMs = REWIND_ON_RESUME_MS,
            // No PC, com ou sem palheta (Configurações → Controles); no celular, sempre o toque.
            strum = settings.controls.strum,
        )
        if (!pauseRequested) session.start()
        interruptions = interruptionsFactory.create().also {
            if (!pauseRequested) it.start(onInterrupted = ::pause, onBusy = ::pauseAudioBusy)
        }
        GameplayUiState.Ready(
            playable.chart.title,
            playable.chart.artist,
            difficulty,
            session,
            settings,
            pauseRequested,
            withoutSeparateGuitar,
            songOffsetMs = songOffsetMs,
            art = art,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        release()
        GameplayUiState.Unavailable(reasonFor(e))
    }

    /** Faixas separadas ou um arquivo só; se as faixas falharem, cai no arquivo só (com aviso). */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // a falha das faixas vira o modo de um arquivo
    private suspend fun prepareSong(audio: ChartAudio?): Pair<SongPlayer, Boolean> {
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

    fun pause() {
        if (state.value is GameplayUiState.Loading) pauseRequested = true
        if (player == null) return // já liberado: saindo ou terminando
        val ready = state.value as? GameplayUiState.Ready ?: return
        ready.session.pause()
        state.update { ready.copy(isPaused = true) }
    }

    private fun pauseAudioBusy() {
        pause()
        state.update { (it as? GameplayUiState.Ready)?.copy(audioBusy = true) ?: it }
    }

    fun resume() {
        if (state.value is GameplayUiState.Loading) pauseRequested = false
        if (player == null) return
        val ready = state.value as? GameplayUiState.Ready ?: return
        ready.session.resume()
        // Pede o foco de áudio de novo (uma ligação o tirou); negado, a pausa explica.
        interruptions?.start(onInterrupted = ::pause, onBusy = ::pauseAudioBusy)
        state.update { ready.copy(isPaused = false, audioBusy = false) }
    }

    /** Ajuste fino na pausa: vale ao continuar e fica salvo para este mapa. */
    fun adjustSongOffset(deltaMs: Int) {
        val ready = state.value as? GameplayUiState.Ready ?: return
        val updated = (songOffsetMs + deltaMs).coerceIn(-MAX_SONG_OFFSET_MS, MAX_SONG_OFFSET_MS)
        if (updated == songOffsetMs) return
        ready.session.adjustOffset((updated - songOffsetMs).toLong())
        songOffsetMs = updated
        state.update { ready.copy(songOffsetMs = updated) }
        viewModelScope.launch { chartOffsets.save(chartId, updated) }
    }

    fun onSessionFinished() {
        val ready = state.value as? GameplayUiState.Ready ?: return
        if (finishing) return
        finishing = true
        release()
        val engine = ready.session.engine
        engine.finish()
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
            cleanStreakMs = engine.longestCleanMs,
        )
        viewModelScope.launch {
            val scoreId = scores.save(record)
            // Também quando o Resultado é pulado: o envio é deste passo, não da tela (spec do ranking §4).
            ranking.submitIfBest(record.copy(id = scoreId), ready.title, ready.artist)
            state.value = GameplayUiState.Finished(scoreId)
        }
    }

    /** Para e libera o áudio, ao terminar ou quando a tela sai de vez ([onCleared]). */
    fun release() {
        interruptions?.stop()
        interruptions = null
        player?.release()
        sfx?.release()
        player = null
        sfx = null
    }

    override fun onCleared() = release()
}

/** Quanto a pista volta ao continuar depois de uma pausa. */
private const val REWIND_ON_RESUME_MS = 500L

/** Falha na leitura do arquivo do mapa (corrompido ou mudou desde a importação). */
private class ChartReadException(cause: Throwable) : Exception("mapa ilegível", cause)

/** Falhas da leitura do mapa ficam separadas das outras (sons, banco): cada uma tem a sua explicação. */
@Suppress("TooGenericExceptionCaught")
private suspend fun ChartTrackLoader.read(chartId: String, difficulty: Difficulty) = try {
    load(chartId, difficulty)
} catch (e: CancellationException) {
    throw e
} catch (e: SecurityException) {
    // Permissão da pasta revogada no meio da leitura.
    throw FolderAccessLostException(e.message.orEmpty(), e)
} catch (e: ChartNotFoundException) {
    throw e
} catch (e: FolderAccessLostException) {
    throw e
} catch (e: Exception) {
    throw ChartReadException(e)
}

/** A explicação na tela para cada falha ao preparar a partida. */
private fun reasonFor(error: Exception): UnavailableReason = when (error) {
    is NoAudioException -> UnavailableReason.NO_AUDIO

    is AudioFailedException -> UnavailableReason.AUDIO_FAILED

    is FolderAccessLostException -> UnavailableReason.FOLDER_ACCESS_LOST

    is ChartNotFoundException -> UnavailableReason.CHART_MISSING

    // O arquivo do mapa não pôde ser lido (corrompido ou mudou desde a importação).
    is ChartReadException -> UnavailableReason.CHART_UNREADABLE

    else -> UnavailableReason.UNEXPECTED
}

private class NoAudioException : Exception("pasta sem áudio")

private class AudioFailedException(cause: Throwable) : Exception("áudio não pôde ser preparado", cause)
