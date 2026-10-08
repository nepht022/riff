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
 * A música da partida tocando no Spotify. O primeiro [play] toca a faixa do começo; os seguintes retomam.
 * Comandos valem na hora (estado otimista) e os eventos do Spotify confirmam ou corrigem; estados de
 * antes do último comando são descartados. A posição é extrapolada do último estado.
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
    private var last: PlaybackState? = null
    private var listening: Job? = null

    override suspend fun prepare(uri: String) {
        remote.connect()
        val connection = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            remote.connection.first { it == SpotifyConnection.Connected || it is SpotifyConnection.Failed }
        }
        if (connection != SpotifyConnection.Connected) throw IOException("Spotify indisponível: $connection")
        trackUri = uri
        listening = scope.launch {
            launch { remote.playback.collect(::onState) }
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                remote.refresh()
            }
        }
    }

    private fun onState(state: PlaybackState?) {
        if (!started) return
        if (state == null) {
            last = last?.frozenAt(positionMs)
            return
        }
        if (state.capturedAtElapsedMs < lastCommandAtMs) return
        if (state.trackUri != trackUri) {
            if (sawOurTrack) completed = true
            return
        }
        sawOurTrack = true
        last = state
        if (state.isPaused && state.positionMs >= state.durationMs - END_TOLERANCE_MS) completed = true
    }

    override fun play() {
        val uri = trackUri ?: return
        val now = nowMs()
        lastCommandAtMs = now
        if (!started) {
            started = true
            remote.play(uri)
            last = PlaybackState(uri, "", "", 0, 0, isPaused = false, capturedAtElapsedMs = now)
        } else {
            remote.resume()
            last = last?.copy(isPaused = false, capturedAtElapsedMs = now)
        }
    }

    override fun pause() {
        if (!started) return
        val position = positionMs
        lastCommandAtMs = nowMs()
        remote.pause()
        last = last?.frozenAt(position)
    }

    override fun release() {
        listening?.cancel()
        listening = null
        if (started && !completed && isPlaying) remote.pause()
    }

    override val positionMs: Long
        get() {
            val state = last ?: return 0
            return if (state.isPaused) state.positionMs else state.positionMs + (nowMs() - state.capturedAtElapsedMs)
        }

    override val isPlaying: Boolean get() = last?.isPaused == false

    override val isCompleted: Boolean get() = completed

    private fun PlaybackState.frozenAt(position: Long) =
        copy(positionMs = position, isPaused = true, capturedAtElapsedMs = nowMs())

    companion object {
        const val REFRESH_INTERVAL_MS = 2_000L
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val END_TOLERANCE_MS = 1_500L
    }
}
