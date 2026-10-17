package com.rodrigomachado.riff.core.spotify.web

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import com.rodrigomachado.riff.core.spotify.ForegroundActivity
import com.rodrigomachado.riff.core.spotify.SpotifyConfig
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import com.spotify.sdk.android.auth.PKCEInformation
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Escopo para ler a fila (e o player) pela API Web. */
private const val QUEUE_SCOPE = "user-read-playback-state"
private const val RESULT_KEY = "riff-spotify-web-auth"
private const val KEY_ALIAS = "riff_spotify_web"
private const val TIMEOUT_MS = 15_000

/** A tela de autorização do Spotify pedindo um código (PKCE), aberta a partir da tela do Riff que está à frente. */
class ActivityCodeLauncher(private val config: SpotifyConfig) : CodeLauncher {
    override suspend fun requestCode(verifier: String, challenge: String): String? = withContext(Dispatchers.Main) {
        val activity = ForegroundActivity.current as? ComponentActivity ?: return@withContext null
        val request = AuthorizationRequest.Builder(config.clientId, AuthorizationResponse.Type.CODE, config.redirectUri)
            .setScopes(arrayOf(QUEUE_SCOPE))
            .setPkceInformation(PKCEInformation(verifier, challenge, "S256"))
            .build()
        suspendCancellableCoroutine { continuation ->
            var launcher: ActivityResultLauncher<Intent>? = null
            launcher = activity.activityResultRegistry.register(RESULT_KEY, StartActivityForResult()) { result ->
                launcher?.unregister()
                val response = AuthorizationClient.getResponse(result.resultCode, result.data)
                val code = response.code.takeIf { response.type == AuthorizationResponse.Type.CODE }
                if (continuation.isActive) continuation.resume(code)
            }
            continuation.invokeOnCancellation { launcher.unregister() }
            launcher.launch(AuthorizationClient.createLoginActivityIntent(activity, request))
        }
    }
}

/** POST de formulário pelo HTTP do próprio Android. */
object UrlConnectionFormPost : FormPost {
    override suspend fun post(url: String, form: Map<String, String>): Pair<Int, String> = withContext(Dispatchers.IO) {
        val body = form.entries.joinToString("&") { (k, v) -> "${k.encoded()}=${v.encoded()}" }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            val stream = if (code >= HTTP_ERROR) connection.errorStream else connection.inputStream
            code to (stream?.use { it.readBytes().decodeToString() } ?: "")
        } finally {
            connection.disconnect()
        }
    }

    private fun String.encoded() = URLEncoder.encode(this, "UTF-8")

    private const val HTTP_ERROR = 400
}

@Module
@InstallIn(SingletonComponent::class)
object SpotifyWebModule {
    @Provides
    @Singleton
    fun webAuth(@ApplicationContext context: Context, config: SpotifyConfig): SpotifyWebAuth = PkceSpotifyWebAuth(
        clientId = config.clientId,
        redirectUri = config.redirectUri,
        store = EncryptedTokenStore(
            context.getSharedPreferences(KEY_ALIAS, Context.MODE_PRIVATE),
            AndroidKeystoreKey(KEY_ALIAS),
        ),
        post = UrlConnectionFormPost,
        launcher = ActivityCodeLauncher(config),
        clock = SystemClock::elapsedRealtime,
    )

    @Provides
    fun queue(api: SpotifyQueueApi): SpotifyQueue = api
}
