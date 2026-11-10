package com.rodrigomachado.riff.core.room

import com.rodrigomachado.riff.core.datastore.RiffPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject

/** O jogador anônimo da Sala: o [uid] e o [refreshToken] que renova o acesso (o `idToken` fica só na memória). */
data class RoomIdentity(val uid: String, val refreshToken: String)

interface RoomIdentityStore {
    suspend fun read(): RoomIdentity?

    suspend fun write(identity: RoomIdentity)
}

/** A identidade nas preferências do DataStore (`room_uid`, `room_refresh_token`), no celular e no PC. */
class DataStoreRoomIdentityStore @Inject constructor(private val preferences: RiffPreferences) : RoomIdentityStore {
    override suspend fun read(): RoomIdentity? {
        val (uid, refreshToken) = preferences.roomIdentity.first()
        return if (uid != null && refreshToken != null) RoomIdentity(uid, refreshToken) else null
    }

    override suspend fun write(identity: RoomIdentity) =
        preferences.setRoomIdentity(identity.uid, identity.refreshToken)
}

/**
 * Login anônimo pela API REST do Identity Toolkit: no primeiro uso, `accounts:signUp`; depois, o `refreshToken`
 * guardado dá um `idToken` novo no `securetoken.googleapis.com`. O `idToken` (1 h) é reaproveitado até 5 min antes
 * de vencer. Sem rede, [NoConnection]; o Google respondendo erro, [RoomHttpError].
 */
class AnonymousAuth(
    private val http: RoomHttp,
    private val store: RoomIdentityStore,
    private val apiKey: String,
    private val clock: () -> Long,
) {
    private class Session(val uid: String, val idToken: String, val expiresAt: Long)

    private val mutex = Mutex()
    private var session: Session? = null

    suspend fun token(): String = current().idToken

    suspend fun uid(): String = current().uid

    /** Joga fora o `idToken` da memória e pega outro (o streaming avisou `auth_revoked`). */
    suspend fun refreshNow() {
        mutex.withLock { session = signIn() }
    }

    private suspend fun current(): Session = mutex.withLock {
        session?.takeIf { clock() < it.expiresAt - RENEW_BEFORE_MS } ?: signIn().also { session = it }
    }

    private suspend fun signIn(): Session {
        val saved = store.read() ?: return signUp()
        val form = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(saved.refreshToken, "UTF-8")
        val reply = http.request("POST", "$REFRESH_URL?key=$apiKey", form, FORM)
        // 400: a conta anônima não existe mais (apagada no console, token revogado): começa outra.
        if (reply.code == HTTP_BAD_REQUEST) return signUp()
        val session = fields(reply) { json ->
            Session(json.getString("user_id"), json.getString("id_token"), expiresAt(json.getString("expires_in"))) to
                json.getString("refresh_token")
        }
        val identity = RoomIdentity(session.first.uid, session.second)
        if (identity != saved) store.write(identity)
        return session.first
    }

    private suspend fun signUp(): Session {
        val body = JSONObject().put("returnSecureToken", true).toString()
        val (session, refreshToken) = fields(http.request("POST", "$SIGN_UP_URL?key=$apiKey", body, JSON)) { json ->
            Session(json.getString("localId"), json.getString("idToken"), expiresAt(json.getString("expiresIn"))) to
                json.getString("refreshToken")
        }
        store.write(RoomIdentity(session.uid, refreshToken))
        return session
    }

    /**
     * Lê a resposta de sucesso com [read]. Um erro, uma página no lugar do JSON (portal de Wi-Fi, proxy) ou um JSON
     * sem os campos esperados viram [RoomHttpError] (nunca um JSONException solto, que fecharia o app).
     */
    private fun <T : Any> fields(reply: HttpReply, read: (JSONObject) -> T): T {
        val value = if (reply.code in HTTP_OK) {
            try {
                read(JSONObject(reply.body))
            } catch (_: JSONException) {
                null
            } catch (_: NumberFormatException) {
                null // expiresIn que não é número
            }
        } else {
            null
        }
        return value ?: throw RoomHttpError(reply.code)
    }

    private fun expiresAt(expiresInSeconds: String) = clock() + expiresInSeconds.toLong() * MS_PER_SECOND

    private companion object {
        const val SIGN_UP_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signUp"
        const val REFRESH_URL = "https://securetoken.googleapis.com/v1/token"
        const val RENEW_BEFORE_MS = 5 * 60_000L
        const val MS_PER_SECOND = 1_000L
        const val HTTP_BAD_REQUEST = 400
        val HTTP_OK = 200..299
        val FORM = mapOf("Content-Type" to "application/x-www-form-urlencoded")
        val JSON = mapOf("Content-Type" to "application/json; charset=utf-8")
    }
}
