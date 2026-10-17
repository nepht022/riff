package com.rodrigomachado.riff.core.spotify.web

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

enum class WebAuthState { NOT_AUTHORIZED, AUTHORIZED, LOST }

/** Autorização da API Web do Spotify (ler a fila). Só [authorize] abre tela, e só as Configurações chamam. */
interface SpotifyWebAuth {
    val state: StateFlow<WebAuthState>

    suspend fun authorize(): Boolean

    /** Token válido (renova se faltar pouco); `null` sem autorização ou sem rede. */
    suspend fun accessToken(): String?

    /** A API recusou o token: renova uma vez e devolve o novo, ou `null`. */
    suspend fun onUnauthorized(): String?
}

/** POST de formulário: código HTTP e corpo. */
fun interface FormPost {
    suspend fun post(url: String, form: Map<String, String>): Pair<Int, String>
}

/** Abre a autorização do Spotify (PKCE) e devolve o código, ou `null` se o usuário recusou ou cancelou. */
fun interface CodeLauncher {
    suspend fun requestCode(verifier: String, challenge: String): String?
}

class PkceSpotifyWebAuth(
    private val clientId: String,
    private val redirectUri: String,
    private val store: TokenStore,
    private val post: FormPost,
    private val launcher: CodeLauncher,
    private val clock: () -> Long,
) : SpotifyWebAuth {
    private val authState = MutableStateFlow(
        when {
            store.read() != null -> WebAuthState.AUTHORIZED
            store.unreadable -> WebAuthState.LOST
            else -> WebAuthState.NOT_AUTHORIZED
        },
    )
    override val state: StateFlow<WebAuthState> = authState.asStateFlow()

    private val lock = Mutex()
    private var access: String? = null
    private var expiresAt = 0L

    override suspend fun authorize(): Boolean {
        val verifier = Pkce.verifier()
        val code = launcher.requestCode(verifier, Pkce.challenge(verifier)) ?: return false
        return lock.withLock {
            val form = mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to redirectUri,
                "client_id" to clientId,
                "code_verifier" to verifier,
            )
            val ok = request(form) == Outcome.OK
            if (ok) authState.value = WebAuthState.AUTHORIZED
            ok
        }
    }

    override suspend fun accessToken(): String? = lock.withLock {
        val current = access
        if (current != null && expiresAt - clock() > RENEW_MARGIN_MS) current else renew()
    }

    override suspend fun onUnauthorized(): String? = lock.withLock {
        access = null
        renew()
    }

    private suspend fun renew(): String? {
        val refresh = store.read() ?: return null
        val form = mapOf("grant_type" to "refresh_token", "refresh_token" to refresh, "client_id" to clientId)
        return when (request(form)) {
            Outcome.OK -> access

            Outcome.REFUSED -> {
                // O Spotify não aceita mais: esquece e espera o usuário autorizar de novo nas Configurações.
                save(null)
                access = null
                authState.value = WebAuthState.LOST
                null
            }

            Outcome.FAILED -> null
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // sem rede: tenta na próxima leitura
    private suspend fun request(form: Map<String, String>): Outcome {
        val (code, body) = try {
            post.post(TOKEN_URL, form)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "token do Spotify: sem conexão")
            return Outcome.FAILED
        }
        // Só o código vai para o log: o corpo traz tokens.
        if (code == HTTP_BAD_REQUEST || code == HTTP_UNAUTHORIZED) {
            Log.w(TAG, "token do Spotify recusado: HTTP $code")
            return Outcome.REFUSED
        }
        if (code !in HTTP_OK) {
            Log.w(TAG, "token do Spotify: HTTP $code")
            return Outcome.FAILED
        }
        return try {
            val json = JSONObject(body)
            access = json.getString("access_token")
            expiresAt = clock() + json.getLong("expires_in") * MS_PER_SECOND
            val refresh = json.optString("refresh_token").takeIf { it.isNotEmpty() }
            if (refresh != null && !save(refresh)) Outcome.FAILED else Outcome.OK
        } catch (e: JSONException) {
            Log.w(TAG, "token do Spotify: resposta inesperada")
            Outcome.FAILED
        }
    }

    /** O Keystore pode falhar (chave invalidada, aparelho ocupado): não derruba o app. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun save(refreshToken: String?): Boolean = try {
        store.write(refreshToken)
        true
    } catch (e: Exception) {
        Log.w(TAG, "token do Spotify não guardado: ${e.javaClass.simpleName}")
        access = null
        false
    }

    private enum class Outcome { OK, REFUSED, FAILED }

    private companion object {
        const val TAG = "RiffQueue"
        const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        const val RENEW_MARGIN_MS = 60_000L
        const val MS_PER_SECOND = 1_000L
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        val HTTP_OK = 200..299
    }
}
