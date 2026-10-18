package com.rodrigomachado.riff.core.spotify.session

import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.model.SpotifyError
import com.rodrigomachado.riff.core.spotify.SpotifyRemote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Correção da posição tocando, medida no S25 contra o SDK (spec da sessão de mídia, Task 3). */
const val SESSION_CORRECTION_MS = -340L

/**
 * [SpotifyRemote] pela sessão de mídia do Android: sem SDK, sem login, sem cadastro no Dashboard. "Conectado" quer
 * dizer que o Spotify está instalado e o Riff pode ver as sessões; a música em si vem de [SpotifySession.snapshot].
 */
class MediaSessionSpotify(
    private val session: SpotifySession,
    private val correctionMs: Long,
    private val scope: CoroutineScope,
) : SpotifyRemote {
    private val connectionState = MutableStateFlow<SpotifyConnection>(SpotifyConnection.Idle)
    private var goingBack: Job? = null

    override val connection: StateFlow<SpotifyConnection> = connectionState.asStateFlow()

    override val playback: StateFlow<PlaybackState?> =
        combine(connectionState, session.snapshot) { connection, snapshot ->
            if (connection == SpotifyConnection.Connected) snapshot?.toPlaybackState(correctionMs) else null
        }.stateIn(scope, SharingStarted.Eagerly, null)

    override val adPlaying: StateFlow<Boolean> =
        combine(connectionState, session.snapshot) { connection, snapshot ->
            connection == SpotifyConnection.Connected && snapshot?.isAd == true
        }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun connect() {
        session.recheck()
        connectionState.value = when {
            !session.isInstalled -> SpotifyConnection.Failed(SpotifyError.NOT_INSTALLED)
            !session.hasPermission -> SpotifyConnection.Failed(SpotifyError.NO_PERMISSION)
            else -> SpotifyConnection.Connected
        }
    }

    override suspend fun refresh(): PlaybackState? {
        connect()
        return if (connectionState.value == SpotifyConnection.Connected) {
            session.snapshot.value?.toPlaybackState(correctionMs)
        } else {
            null
        }
    }

    /**
     * Toca [trackUri] do começo. A atual recomeça; outra só pode ser a anterior ("Jogar de novo" depois que o Spotify
     * passou para a seguinte): volta com ⏮, que mantém a playlist e a fila (tocar por endereço trocaria a playlist).
     * Se ela não chegar, nada mais é feito: a partida percebe pelo prazo de começo.
     */
    override fun play(trackUri: String) {
        goingBack?.cancel()
        if (session.snapshot.value?.mediaId == trackUri) {
            session.seekTo(0)
            session.play()
            return
        }
        goingBack = scope.launch {
            repeat(MAX_PREVIOUS) {
                session.skipToPrevious()
                val arrived = withTimeoutOrNull(PREVIOUS_WAIT_MS) {
                    session.snapshot.first { it?.mediaId == trackUri }
                }
                if (arrived != null) return@launch
            }
        }
    }

    override fun pause() = session.pause()

    override fun resume() = session.play()

    override fun seekTo(positionMs: Long) = session.seekTo(positionMs)

    override fun skipNext() = session.skipToNext()

    override fun skipPrevious() = session.skipToPrevious()

    private companion object {
        /** Passados os primeiros segundos, o primeiro ⏮ só reinicia a atual; o segundo chega à anterior. */
        const val MAX_PREVIOUS = 2
        const val PREVIOUS_WAIT_MS = 1_500L
    }
}
