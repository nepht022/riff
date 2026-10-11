package com.rodrigomachado.riff.core.spotify

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.model.SpotifyError
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
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

/**
 * [SpotifyRemote] com o App Remote SDK. Uma conexão por processo; reconecta sozinho se ela cair.
 * O pedido de autorização do Spotify só abre a partir de uma tela: a conexão usa a Activity em primeiro
 * plano e, se nenhuma estiver aberta, espera a próxima. O pedido de autorização do próprio App Remote é
 * bloqueado pelo Android atual; sem autorização, o login da biblioteca de auth abre uma vez a partir da tela
 * do Riff e, na volta, a conexão é refeita.
 */
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
    private var connectWhenVisible = false
    private var triedAuthorization = false

    init {
        ForegroundActivity.onResumed = { activity ->
            // As telas do próprio login do Spotify também passam por aqui: espera voltar para o Riff.
            if (connectWhenVisible && !activity.javaClass.name.startsWith(SPOTIFY_SDK_PACKAGE)) startConnecting()
        }
    }

    override val connection: StateFlow<SpotifyConnection> = connectionState.asStateFlow()
    override val playback: StateFlow<PlaybackState?> = playbackState.asStateFlow()

    override fun connect() {
        val current = connectionState.value
        if (current == SpotifyConnection.Connecting || current == SpotifyConnection.Connected) return
        triedAuthorization = false
        when {
            !config.isConfigured -> connectionState.value = SpotifyConnection.Failed(SpotifyError.NOT_CONFIGURED)

            !SpotifyAppRemote.isSpotifyInstalled(context) ->
                connectionState.value = SpotifyConnection.Failed(SpotifyError.NOT_INSTALLED)

            else -> startConnecting()
        }
    }

    private fun startConnecting() {
        connectionState.value = SpotifyConnection.Connecting
        val activity = ForegroundActivity.current
        if (activity == null) {
            connectWhenVisible = true
            return
        }
        connectWhenVisible = false
        Log.i(TAG, "Conectando ao Spotify")
        val params = ConnectionParams.Builder(config.clientId)
            .setRedirectUri(config.redirectUri)
            // O pedido do próprio SDK sai do Spotify em segundo plano e o Android 14+ bloqueia; sem ele a falha
            // volta na hora e o login abre a partir da tela do Riff (authorize).
            .showAuthView(false)
            .build()
        // Tem que ser a Activity: com o contexto do app a conexão fica pendurada em "Conectando" (testado no
        // aparelho em 2026-10-11). O SDK pode segurar a tela até a conexão terminar; é o preço de conectar.
        SpotifyAppRemote.connect(
            activity,
            params,
            object : Connector.ConnectionListener {
                override fun onConnected(appRemote: SpotifyAppRemote) {
                    Log.i(TAG, "Conectado ao Spotify")
                    remote = appRemote
                    reconnectAttempt = 0
                    connectionState.value = SpotifyConnection.Connected
                    subscribe(appRemote)
                }

                override fun onFailure(error: Throwable) {
                    // O motivo exato só aparece aqui (Dashboard, autorização, app do Spotify): vai para o logcat.
                    Log.w(TAG, "Falha ao conectar ao Spotify", error)
                    val reason = error.toSpotifyError()
                    if (reason == SpotifyError.NOT_AUTHORIZED && !triedAuthorization) authorize() else onLost(reason)
                }
            },
        )
    }

    /** Abre o login do Spotify pedindo o controle remoto; a conexão é refeita quando o Riff volta à tela. */
    private fun authorize() {
        val activity = ForegroundActivity.current as? ComponentActivity ?: return onLost(SpotifyError.NOT_AUTHORIZED)
        triedAuthorization = true
        connectWhenVisible = true
        val request = AuthorizationRequest.Builder(
            config.clientId,
            AuthorizationResponse.Type.TOKEN,
            config.redirectUri,
        ).setScopes(arrayOf(APP_REMOTE_SCOPE)).build()
        var launcher: ActivityResultLauncher<Intent>? = null
        launcher = activity.activityResultRegistry.register(AUTH_RESULT_KEY, StartActivityForResult()) { result ->
            launcher?.unregister()
            val response = AuthorizationClient.getResponse(result.resultCode, result.data)
            if (response.type != AuthorizationResponse.Type.TOKEN) {
                Log.w(TAG, "Autorização do Spotify não concluída: ${response.type} ${response.error.orEmpty()}")
            }
        }
        launcher.launch(AuthorizationClient.createLoginActivityIntent(activity, request))
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
        // A hora do pedido, não a da resposta: uma resposta lenta não pode parecer mais nova que um comando.
        val requestedAt = SystemClock.elapsedRealtime()
        return suspendCancellableCoroutine { continuation ->
            appRemote.playerApi.playerState
                .setResultCallback { state ->
                    val playback = state.toPlaybackState(requestedAt)
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

    override fun seekTo(positionMs: Long) {
        remote?.playerApi?.seekTo(positionMs)
    }

    private companion object {
        const val TAG = "RiffSpotify"
        const val APP_REMOTE_SCOPE = "app-remote-control"
        const val AUTH_RESULT_KEY = "riff-spotify-auth"
        const val SPOTIFY_SDK_PACKAGE = "com.spotify.sdk"
    }
}
