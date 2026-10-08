package com.rodrigomachado.riff.core.spotify

import android.content.Context
import android.os.SystemClock
import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.model.SpotifyError
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** [SpotifyRemote] com o App Remote SDK. Uma conexão por processo; reconecta sozinho se ela cair. */
@Singleton
class AppRemoteSpotify @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: SpotifyConfig,
) : SpotifyRemote {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectionState = MutableStateFlow<SpotifyConnection>(SpotifyConnection.Idle)
    private val playbackState = MutableStateFlow<PlaybackState?>(null)
    private var remote: SpotifyAppRemote? = null
    private var subscription: Subscription<PlayerState>? = null
    private var reconnectAttempt = 0

    override val connection: StateFlow<SpotifyConnection> = connectionState.asStateFlow()
    override val playback: StateFlow<PlaybackState?> = playbackState.asStateFlow()

    override fun connect() {
        val current = connectionState.value
        if (current == SpotifyConnection.Connecting || current == SpotifyConnection.Connected) return
        when {
            !config.isConfigured -> connectionState.value = SpotifyConnection.Failed(SpotifyError.NOT_CONFIGURED)

            !SpotifyAppRemote.isSpotifyInstalled(context) ->
                connectionState.value = SpotifyConnection.Failed(SpotifyError.NOT_INSTALLED)

            else -> startConnecting()
        }
    }

    private fun startConnecting() {
        connectionState.value = SpotifyConnection.Connecting
        val params = ConnectionParams.Builder(config.clientId)
            .setRedirectUri(config.redirectUri)
            .showAuthView(true)
            .build()
        SpotifyAppRemote.connect(
            context,
            params,
            object : Connector.ConnectionListener {
                override fun onConnected(appRemote: SpotifyAppRemote) {
                    remote = appRemote
                    reconnectAttempt = 0
                    connectionState.value = SpotifyConnection.Connected
                    subscribe(appRemote)
                }

                override fun onFailure(error: Throwable) = onLost(error.toSpotifyError())
            },
        )
    }

    private fun subscribe(appRemote: SpotifyAppRemote) {
        subscription?.cancel()
        subscription = appRemote.playerApi.subscribeToPlayerState()
            .setEventCallback { state -> playbackState.value = state.toPlaybackState(SystemClock.elapsedRealtime()) }
            .apply { setErrorCallback { error -> onLost(error.toSpotifyError()) } }
    }

    private fun onLost(error: SpotifyError) {
        subscription?.cancel()
        subscription = null
        remote?.let(SpotifyAppRemote::disconnect)
        remote = null
        playbackState.value = null
        connectionState.value = SpotifyConnection.Failed(error)
        if (ReconnectPolicy.shouldRetry(error)) {
            val wait = ReconnectPolicy.delayMs(reconnectAttempt++)
            scope.launch {
                delay(wait)
                if (connectionState.value is SpotifyConnection.Failed) connect()
            }
        }
    }

    override suspend fun refresh(): PlaybackState? {
        val appRemote = remote ?: return null
        return suspendCancellableCoroutine { continuation ->
            appRemote.playerApi.playerState
                .setResultCallback { state ->
                    val playback = state.toPlaybackState(SystemClock.elapsedRealtime())
                    playbackState.value = playback
                    if (continuation.isActive) continuation.resume(playback)
                }
                .setErrorCallback { if (continuation.isActive) continuation.resume(null) }
        }
    }

    override fun play(trackUri: String) {
        remote?.playerApi?.play(trackUri)
    }

    override fun pause() {
        remote?.playerApi?.pause()
    }

    override fun resume() {
        remote?.playerApi?.resume()
    }
}
