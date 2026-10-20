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

/**
 * Correção da posição tocando, medida no S25 contra o SDK em 2026-10-18: a sessão e o SDK ficaram a ±3 ms um do outro
 * tocando (os ~250 ms de salto ao retomar acontecem nos dois e o [com.rodrigomachado.riff.core.spotify.SpotifySongPlayer]
 * já trata). Zero mantém valendo os ajustes por música feitos com o SDK.
 */
const val SESSION_CORRECTION_MS = 0L

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
     * passou para a seguinte): volta a atual ao começo e dá ⏮, que mantém a playlist e a fila (tocar por endereço
     * trocaria a playlist). Se ela não chegar, nada mais é feito: a partida percebe pelo prazo de começo.
     */
    override fun play(trackUri: String) {
        goingBack?.cancel()
        if (session.snapshot.value?.mediaId == trackUri) {
            session.seekTo(0)
            session.play()
            return
        }
        goingBack = scope.launch {
            // Do começo da atual, o ⏮ do Spotify sempre vai para a anterior (a posição da sessão fica parada entre os
            // avisos e não serve para decidir entre reiniciar e voltar). O mesmo do ⏮ da pausa da partida.
            session.seekTo(0)
            session.skipToPrevious()
            val arrived = withTimeoutOrNull(ARRIVAL_WAIT_MS) {
                session.snapshot.first { it?.mediaId == trackUri }
            } ?: return@launch
            // Chegou do começo; se ficou pausada no ⏮, manda tocar (sem voltar a posição: a pista já pode estar andando).
            if (arrived?.state != android.media.session.PlaybackState.STATE_PLAYING) session.play()
        }
    }

    override fun cancelPendingCommands() {
        goingBack?.cancel()
        goingBack = null
    }

    override fun pause() = session.pause()

    override fun resume() = session.play()

    override fun seekTo(positionMs: Long) = session.seekTo(positionMs)

    override fun skipNext() = session.skipToNext()

    override fun skipPrevious() = session.skipToPrevious()

    private companion object {
        /** Menos que o prazo de começo da partida (5 s): chegando depois, ela já pede para voltar a música. */
        const val ARRIVAL_WAIT_MS = 4_000L
    }
}
