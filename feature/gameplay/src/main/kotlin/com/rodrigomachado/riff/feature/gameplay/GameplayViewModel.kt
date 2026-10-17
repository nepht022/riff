package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.audio.LocalSong
import com.rodrigomachado.riff.core.audio.MusicStartFinder
import com.rodrigomachado.riff.core.audio.PlaybackInterruptions
import com.rodrigomachado.riff.core.audio.PlaybackInterruptionsFactory
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
import com.rodrigomachado.riff.core.data.SpotifyControls
import com.rodrigomachado.riff.core.data.TrackMatchRepository
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.game.ChartAudio
import com.rodrigomachado.riff.core.data.game.ChartNotFoundException
import com.rodrigomachado.riff.core.data.game.ChartTrackLoader
import com.rodrigomachado.riff.core.data.queue.MatchActivity
import com.rodrigomachado.riff.core.game.TimeSource
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.model.ScoreRecord
import com.rodrigomachado.riff.core.model.Settings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class UnavailableReason {
    NO_AUDIO,
    CHART_MISSING,
    CHART_UNREADABLE,
    FOLDER_ACCESS_LOST,
    AUDIO_FAILED,
    SPOTIFY_UNAVAILABLE,
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
        /** Modo Spotify: a faixa tocando; a pausa mostra anterior, tocar e próxima. */
        val spotifyTrackUri: String? = null,
        /** Mapa só sugerido: no lugar da pausa, o painel de confirmação; a faixa só toca depois de confirmar. */
        val awaitingConfirmation: Boolean = false,
    ) : GameplayUiState {
        val spotifyControls: Boolean get() = spotifyTrackUri != null
    }

    data class Unavailable(val reason: UnavailableReason) : GameplayUiState

    data class Finished(val scoreId: Long) : GameplayUiState

    /** Modo Spotify: a partida acabou e o Riff segue para a próxima música (o Tocando agora espera até [waitMs]). */
    data class NextSong(val difficulty: Difficulty, val fromTrackUri: String, val waitMs: Long) : GameplayUiState
}

// A partida: carregar, pausa, sincronia, troca de música e confirmação da sugestão; dividir espalharia o estado.
@Suppress("TooManyFunctions")
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
    private val interruptionsFactory: PlaybackInterruptionsFactory,
    private val artImages: ChartArtImages,
    private val spotify: SpotifyControls,
    private val matchActivity: MatchActivity,
) : ViewModel() {
    private val chartId: String = checkNotNull(savedStateHandle["chartId"])
    private val difficulty: Difficulty = Difficulty.valueOf(checkNotNull(savedStateHandle["difficulty"]))
    private val spotifyTrackUri: String? = savedStateHandle["spotifyTrackUri"]
    private val trackOffsetMs: Int = savedStateHandle["trackOffsetMs"] ?: 0
    private val confirmFirst: Boolean = savedStateHandle["confirmFirst"] ?: false
    private val spotifyOffsets = SpotifySongOffset(trackMatches, musicStarts)
    private val state = MutableStateFlow<GameplayUiState>(GameplayUiState.Loading)
    val uiState: StateFlow<GameplayUiState> = state.asStateFlow()

    private var player: SongPlayer? = null
    private var sfx: SfxPlayer? = null

    // Biblioteca: ligação, outro app tocando ou fone desconectado pausam a partida (no Spotify, o Spotify cuida).
    private var interruptions: PlaybackInterruptions? = null
    private var finishing = false
    private var songOffsetMs = 0

    // O app pode ir para segundo plano enquanto a música carrega: a partida começa pausada.
    private var pauseRequested = false

    init {
        // Da abertura até a tela sair de vez, a fila do Spotify não baixa nem apaga (e este mapa vira "jogado").
        addCloseable { matchActivity.leave(chartId) }
        viewModelScope.launch {
            matchActivity.enter(chartId)
            state.value = load()
            if ((state.value as? GameplayUiState.Ready)?.awaitingConfirmation == true) watchForSongChange()
        }
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
        if (!pauseRequested && !confirmFirst) session.start()
        if (spotifyTrackUri == null) {
            interruptions = interruptionsFactory.create().also { if (!pauseRequested) it.start { pause() } }
        }
        GameplayUiState.Ready(
            playable.chart.title,
            playable.chart.artist,
            difficulty,
            session,
            settings,
            pauseRequested || confirmFirst,
            withoutSeparateGuitar,
            songOffsetMs = songOffsetMs,
            art = art,
            spotifyTrackUri = spotifyTrackUri,
            awaitingConfirmation = confirmFirst,
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
        interruptions?.start { pause() } // pede o foco de áudio de novo (uma ligação o tirou)
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
        val uri = spotifyTrackUri
        val goOn = uri != null && ready.settings.spotifyNextSongDirect
        // Seguindo para a próxima, a música não para: as notas podem ter acabado no outro, e é o Spotify que troca.
        val waitMs = if (goOn && uri != null) nextSongWaitMs(uri) else 0L
        release(keepMusic = goOn)
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
        )
        viewModelScope.launch {
            val scoreId = scores.save(record)
            // Spotify, "Ir direto para a próxima": o placar fica salvo e o Tocando agora começa a próxima música.
            state.value = if (goOn && uri != null) {
                GameplayUiState.NextSong(difficulty, uri, waitMs)
            } else {
                GameplayUiState.Finished(scoreId)
            }
        }
    }

    /**
     * As notas acabaram antes da música (outro, aplausos): espera o que falta dela + 10 s. Já em outra faixa (foi a
     * troca que acabou a partida) ou sem estado do Spotify, o prazo curto da troca já pedida.
     */
    private fun nextSongWaitMs(uri: String): Long {
        val now = spotify.playback.value?.takeIf { it.trackUri == uri } ?: return CHANGE_WAIT_MS
        return (now.durationMs - now.positionMs).coerceAtLeast(0) + SONG_END_MARGIN_MS
    }

    /**
     * Modo Spotify, na pausa: troca a música no Spotify e larga a partida, sem salvar o placar (a tela sai em
     * seguida). Larga antes de pedir a troca: assim a partida não reage à faixa nova. Verdadeiro se pediu a troca.
     */
    fun changeSong(forward: Boolean): Boolean {
        // Só uma vez: a partida já largada (segundo toque durante a saída da tela) não pede outra troca.
        if (spotifyTrackUri == null || player == null || finishing) return false
        release()
        if (forward) spotify.next() else spotify.previousTrack()
        return true
    }

    private var songChangeWatch: Job? = null

    /** Com o painel aberto, o Spotify passou de música: a sugestão não vale mais; segue para a música nova. */
    private fun watchForSongChange() {
        val uri = spotifyTrackUri ?: return
        songChangeWatch = viewModelScope.launch {
            spotify.playback.first { it != null && it.trackUri != uri }
            if ((state.value as? GameplayUiState.Ready)?.awaitingConfirmation != true) return@launch
            release()
            state.value = GameplayUiState.NextSong(difficulty, uri, CHANGE_WAIT_MS)
        }
    }

    /** Painel de confirmação: grava o mapa sugerido como o desta faixa e começa (a faixa toca do começo). */
    fun confirmAndPlay() {
        val ready = state.value as? GameplayUiState.Ready ?: return
        val uri = spotifyTrackUri
        if (!ready.awaitingConfirmation || uri == null || player == null) return
        songChangeWatch?.cancel()
        state.value = ready.copy(awaitingConfirmation = false, isPaused = false)
        viewModelScope.launch { trackMatches.save(uri, chartId, confidence = 1f, isManual = true) }
        ready.session.start()
    }

    /** Para e libera o áudio, ao terminar ou quando a tela sai de vez ([onCleared]). */
    fun release(keepMusic: Boolean = false) {
        interruptions?.stop()
        interruptions = null
        player?.let { if (keepMusic) it.releaseKeepingMusic() else it.release() }
        sfx?.release()
        player = null
        sfx = null
    }

    override fun onCleared() = release()
}

/** Quanto o Tocando agora espera o Spotify trocar quando a troca já foi pedida (igual ao `NowPlayingAutoPlay`). */
internal const val CHANGE_WAIT_MS = 6_000L

/** Folga depois do fim da música para o Spotify passar para a próxima (spec Spotify contínuo §3). */
private const val SONG_END_MARGIN_MS = 10_000L

/** Quanto a biblioteca volta ao continuar depois de uma pausa. */
private const val LIBRARY_REWIND_MS = 500L

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

    is SpotifyFailedException -> UnavailableReason.SPOTIFY_UNAVAILABLE

    is FolderAccessLostException -> UnavailableReason.FOLDER_ACCESS_LOST

    is ChartNotFoundException -> UnavailableReason.CHART_MISSING

    // O arquivo do mapa não pôde ser lido (corrompido ou mudou desde a importação).
    is ChartReadException -> UnavailableReason.CHART_UNREADABLE

    else -> UnavailableReason.UNEXPECTED
}

private class NoAudioException : Exception("pasta sem áudio")

private class AudioFailedException(cause: Throwable) : Exception("áudio não pôde ser preparado", cause)

private class SpotifyFailedException(cause: Throwable) : Exception("Spotify não pôde tocar", cause)
