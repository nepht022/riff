package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.audio.SongPlayer
import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * A música da partida tocando no Spotify. O primeiro [play] toca a faixa do começo; os seguintes retomam
 * do ponto onde o jogo parou. O Spotify demora a obedecer: depois de [play] a posição fica parada
 * ([isStarting]) até ele confirmar que está tocando; depois de [pause] o jogo fica onde parou, mesmo que o
 * Spotify pare um pouco depois. Estados de antes do último comando são descartados. A posição é extrapolada
 * do último estado.
 */
class SpotifySongPlayer(
    private val remote: SpotifyRemote,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
) : SongPlayer {
    private var trackUri: String? = null
    private var started = false
    private var sawOurTrack = false
    private var completed = false
    private var lastCommandAtMs = Long.MIN_VALUE
    private var startedAtMs = 0L
    private var starting = false

    // Conexão caiu: a posição fica congelada e o que o Spotify mostrar na volta só vale no próximo play.
    private var disconnected = false
    private var afterReconnect: PlaybackState? = null
    private var last: PlaybackState? = null
    private var listening: Job? = null

    // Saiu da partida enquanto o Spotify conectava: o prepare não começa a consultar o Spotify depois.
    private var released = false

    override suspend fun prepare(uri: String) {
        remote.connect()
        val connection = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            remote.connection.first { it == SpotifyConnection.Connected || it is SpotifyConnection.Failed }
        }
        if (connection != SpotifyConnection.Connected) throw IOException("Spotify indisponível: $connection")
        if (released) return
        trackUri = uri
        listening = scope.launch {
            launch { remote.playback.collect(::onState) }
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                remote.refresh()
                pauseIfOurTrackNeverStarted()
                pauseIfSpotifyNeverResumed()
            }
        }
    }

    private fun onState(state: PlaybackState?) {
        when {
            !started -> Unit

            state == null -> {
                last = last?.frozenAt(positionMs)
                disconnected = true
            }

            disconnected -> afterReconnect = state

            state.capturedAtElapsedMs < lastCommandAtMs -> Unit

            state.trackUri != trackUri -> if (sawOurTrack) completed = true

            else -> onOurTrack(state)
        }
    }

    private fun onOurTrack(state: PlaybackState) {
        val wasNearTheEnd = sawOurTrack && isPlaying && positionMs >= state.durationMs - NEAR_END_MS
        sawOurTrack = true
        val pausedByTheGame = last?.isPaused == true
        // Pausado pelo jogo (ou esperando o Spotify voltar), a pista fica onde parou: o Spotify obedece depois.
        if (!state.isPaused || !pausedByTheGame) {
            starting = false
            last = state
        }
        if (!state.isPaused) return
        val pausedAtTheEnd = state.positionMs >= state.durationMs - END_TOLERANCE_MS
        // Sem autoplay, o Spotify para no começo da mesma faixa quando ela acaba.
        val stoppedBackAtTheStart = wasNearTheEnd && state.positionMs < END_TOLERANCE_MS
        if (pausedAtTheEnd || stoppedBackAtTheStart) completed = true
    }

    /** O Spotify recusou ou não achou a faixa: pausa em vez de jogar em silêncio; o próximo play tenta de novo. */
    private fun pauseIfOurTrackNeverStarted() {
        val waitingForOurTrack = started && !sawOurTrack && isPlaying
        if (waitingForOurTrack && nowMs() - startedAtMs > START_TIMEOUT_MS) {
            started = false
            starting = false
            last = last?.frozenAt(0)
        }
    }

    /** Mandou continuar e o Spotify não voltou: o jogo pausa em vez de esperar para sempre. */
    private fun pauseIfSpotifyNeverResumed() {
        if (starting && sawOurTrack && nowMs() - lastCommandAtMs > START_TIMEOUT_MS) starting = false
    }

    override fun play() {
        val uri = trackUri ?: return
        // Sem conexão o comando se perderia e o jogo andaria sem música: continua pausado até ela voltar.
        when (remote.connection.value) {
            SpotifyConnection.Connected -> Unit

            is SpotifyConnection.Failed -> return remote.connect()

            // erro sem nova tentativa automática
            else -> return
        }
        val now = nowMs()
        lastCommandAtMs = now
        when {
            // Ainda não começou (ou pausou, ou a conexão caiu, antes de o Spotify começar a nossa faixa): toca do
            // começo, e o prazo para ela começar conta a partir daqui.
            !started || !sawOurTrack -> {
                disconnected = false
                afterReconnect = null
                started = true
                startedAtMs = now
                starting = true
                remote.play(uri)
                last = PlaybackState(uri, "", "", 0, 0, isPaused = true, capturedAtElapsedMs = now)
            }

            disconnected -> resumeAfterReconnecting(uri, now)

            else -> resumeWhereTheGameStopped()
        }
    }

    /** O Spotify para um pouco depois do jogo: volta para onde a pista parou antes de continuar. */
    private fun resumeWhereTheGameStopped() {
        remote.seekTo(positionMs)
        remote.resume()
        starting = true
    }

    /** Volta a nossa faixa para onde o jogo parou; se o Spotify já estiver lá, só retoma. */
    private fun resumeAfterReconnecting(uri: String, now: Long) {
        val position = positionMs
        val back = afterReconnect
        disconnected = false
        afterReconnect = null
        if (back?.trackUri == uri) {
            remote.seekTo(position)
            remote.resume()
        } else {
            remote.play(uri)
            remote.seekTo(position)
        }
        // Como numa retomada normal: a pista espera o Spotify confirmar que voltou.
        starting = true
        last = last?.copy(positionMs = position, isPaused = true, capturedAtElapsedMs = now)
    }

    override fun pause() {
        if (!started) return
        val position = positionMs
        starting = false
        lastCommandAtMs = nowMs()
        remote.pause()
        last = last?.frozenAt(position)
    }

    override fun release() {
        released = true
        listening?.cancel()
        listening = null
        if (started && !completed && isPlaying) remote.pause()
    }

    override val positionMs: Long
        get() {
            val state = last ?: return 0
            return if (state.isPaused) state.positionMs else state.positionMs + (nowMs() - state.capturedAtElapsedMs)
        }

    override val isPlaying: Boolean get() = starting || last?.isPaused == false

    override val isStarting: Boolean get() = starting

    override val isCompleted: Boolean get() = completed

    private fun PlaybackState.frozenAt(position: Long) =
        copy(positionMs = position, isPaused = true, capturedAtElapsedMs = nowMs())

    companion object {
        const val REFRESH_INTERVAL_MS = 2_000L
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val END_TOLERANCE_MS = 1_500L
        const val START_TIMEOUT_MS = 5_000L
        private const val NEAR_END_MS = END_TOLERANCE_MS + REFRESH_INTERVAL_MS
    }
}
