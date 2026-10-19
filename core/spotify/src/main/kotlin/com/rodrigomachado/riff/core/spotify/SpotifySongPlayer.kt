package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.audio.PlayerNotice
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

    // De onde o Spotify (re)começa: um "tocando" ainda nessa posição é antes de o áudio andar.
    private var startFromMs = 0L

    // O jogo pausou e o Spotify ainda não confirmou: um "tocando" agora é atraso dele, não retomada de fora.
    private var pauseUnconfirmed = false

    // A sessão sumiu (permissão, Spotify fechado, anúncio): a posição fica congelada e o que o Spotify mostrar na volta
    // só vale no próximo play, menos depois de um anúncio.
    private var disconnected = false

    // Houve anúncio desde que a sessão sumiu: a música volta sozinha, e outra faixa depois dele é a seguinte.
    private var adBreak = false
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
            launch { remote.adPlaying.collect { if (it) adBreak = true } }
            launch { remote.playback.collect(::onState) }
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                remote.refresh()
                // O Spotify recusou ou não achou a faixa: pausa em vez de jogar em silêncio; o próximo play tenta
                // de novo.
                val waitingForOurTrack = started && !sawOurTrack && isPlaying
                if (waitingForOurTrack && nowMs() - startedAtMs > START_TIMEOUT_MS) {
                    started = false
                    starting = false
                    trackRefused = true
                    last = last?.frozenAt(0, nowMs())
                }
                // Mandou continuar e o Spotify não voltou: o jogo pausa em vez de esperar para sempre.
                if (starting && sawOurTrack && nowMs() - lastCommandAtMs > START_TIMEOUT_MS) starting = false
            }
        }
    }

    private fun onState(state: PlaybackState?) {
        when {
            !started -> Unit

            state == null -> {
                last = last?.frozenAt(positionMs, nowMs())
                disconnected = true
            }

            disconnected -> onSessionBack(state)

            state.capturedAtElapsedMs < lastCommandAtMs -> Unit

            state.trackUri != trackUri -> if (sawOurTrack) completed = true

            // A faixa já tocava quando a partida começou: logo depois do comando de tocar do começo, o Spotify
            // ainda manda a posição antiga. Recomeçada, ela não pode estar além do tempo desde o comando; aceitar
            // faria o relógio pular para lá e voltar, com as notas do meio já perdidas (esbranquiçadas).
            !sawOurTrack && state.positionMs > state.capturedAtElapsedMs - startedAtMs + START_POSITION_SLACK_MS ->
                Unit

            else -> onOurTrack(state)
        }
    }

    /** Depois de um anúncio a música segue sozinha; noutros casos espera o Continuar. */
    private fun onSessionBack(state: PlaybackState) {
        if (!adBreak) {
            afterReconnect = state
            return
        }
        adBreak = false
        disconnected = false
        afterReconnect = null
        when {
            state.trackUri == trackUri -> onOurTrack(state)
            sawOurTrack -> completed = true
        }
    }

    private fun onOurTrack(state: PlaybackState) {
        val wasNearTheEnd = sawOurTrack && isPlaying && positionMs >= state.durationMs - NEAR_END_MS
        sawOurTrack = true
        if (isEarly(state)) return
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

    /**
     * Um "tocando" que chegou cedo demais. Registro no S25: depois de tocar/continuar, o Spotify avisa "tocando" na
     * posição de partida antes de o áudio andar (aceitar fazia a pista correr ~100 ms na frente e voltar); e, logo
     * depois de o jogo pausar, ainda avisa "tocando" antes de obedecer (o menu de pausa piscava).
     */
    private fun isEarly(state: PlaybackState): Boolean {
        if (state.isPaused) {
            pauseUnconfirmed = false
            return false
        }
        val notMovingYet = starting && state.positionMs <= startFromMs
        val lateAfterPause = pauseUnconfirmed && nowMs() - lastCommandAtMs < PAUSE_CONFIRM_MS
        return notMovingYet || lateAfterPause
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
        trackRefused = false
        when {
            // Ainda não começou (ou pausou, ou a conexão caiu, antes de o Spotify começar a nossa faixa): toca do
            // começo, e o prazo para ela começar conta a partir daqui.
            !started || !sawOurTrack -> {
                disconnected = false
                afterReconnect = null
                started = true
                startedAtMs = now
                starting = true
                startFromMs = 0
                pauseUnconfirmed = false
                remote.play(uri)
                last = PlaybackState(uri, "", "", 0, 0, isPaused = true, capturedAtElapsedMs = now)
            }

            disconnected -> resumeAfterReconnecting(uri, now)

            else -> resumeWhereTheGameStopped()
        }
    }

    /** O Spotify para um pouco depois do jogo: volta para onde a pista parou antes de continuar. */
    private fun resumeWhereTheGameStopped() {
        startFromMs = positionMs
        pauseUnconfirmed = false
        remote.seekTo(positionMs)
        remote.resume()
        starting = true
    }

    /**
     * A sessão voltou: na nossa faixa, volta para onde o jogo parou e retoma. Noutra faixa, a sessão de mídia não tem
     * como pôr a nossa no lugar sem trocar a playlist: a partida espera e a pausa pede para voltar a música no Spotify.
     */
    private fun resumeAfterReconnecting(uri: String, now: Long) {
        val back = afterReconnect
        if (back?.trackUri != uri) {
            trackRefused = back != null
            return
        }
        val position = positionMs
        startFromMs = position
        pauseUnconfirmed = false
        disconnected = false
        afterReconnect = null
        remote.seekTo(position)
        remote.resume()
        // Como numa retomada normal: a pista espera o Spotify confirmar que voltou.
        starting = true
        last = last?.copy(positionMs = position, isPaused = true, capturedAtElapsedMs = now)
    }

    override fun pause() {
        if (!started) return
        val position = positionMs
        // Só manda pausar se o Spotify toca (ou está começando) a nossa: se ele parou sozinho (anúncio, carregando,
        // sessão perdida), um pause pararia o anúncio de vez e a música não voltaria.
        val spotifyPlaying = !disconnected && (starting || last?.isPaused == false)
        starting = false
        lastCommandAtMs = nowMs()
        if (spotifyPlaying) {
            pauseUnconfirmed = true
            remote.pause()
        }
        last = last?.frozenAt(position, nowMs())
    }

    override fun release() {
        released = true
        listening?.cancel()
        listening = null
        if (started && !completed && isPlaying) remote.pause()
    }

    override fun releaseKeepingMusic() {
        released = true
        listening?.cancel()
        listening = null
    }

    override val positionMs: Long
        get() {
            val state = last ?: return 0
            return if (state.isPaused) state.positionMs else state.positionMs + (nowMs() - state.capturedAtElapsedMs)
        }

    override val isPlaying: Boolean get() = starting || last?.isPaused == false

    override val isStarting: Boolean get() = starting

    override val isCompleted: Boolean get() = completed

    private var trackRefused = false

    override val notice: PlayerNotice?
        get() = when {
            trackRefused -> PlayerNotice.GO_BACK_TO_TRACK
            remote.adPlaying.value -> PlayerNotice.AD
            disconnected || remote.connection.value is SpotifyConnection.Failed -> PlayerNotice.SOURCE_LOST
            else -> null
        }

    companion object {
        const val REFRESH_INTERVAL_MS = 2_000L
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val END_TOLERANCE_MS = 1_500L
        const val START_TIMEOUT_MS = 5_000L

        /** Quanto o Spotify pode demorar para obedecer a pausa (no S25, ~280 ms). */
        const val PAUSE_CONFIRM_MS = 1_500L

        /** Folga para a posição do recomeço (o Spotify arredonda e mede de outro relógio). */
        const val START_POSITION_SLACK_MS = 1_500L
        private const val NEAR_END_MS = END_TOLERANCE_MS + REFRESH_INTERVAL_MS
    }
}

/** Parado em [position] desde [now]. */
private fun PlaybackState.frozenAt(position: Long, now: Long) =
    copy(positionMs = position, isPaused = true, capturedAtElapsedMs = now)
