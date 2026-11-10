package com.rodrigomachado.riff.core.room.testing

import com.rodrigomachado.riff.core.room.AnonymousAuth
import com.rodrigomachado.riff.core.room.FirebaseDb
import com.rodrigomachado.riff.core.room.HttpReply
import com.rodrigomachado.riff.core.room.JsonTree
import com.rodrigomachado.riff.core.room.NoConnection
import com.rodrigomachado.riff.core.room.RoomDevice
import com.rodrigomachado.riff.core.room.RoomHttp
import com.rodrigomachado.riff.core.room.RoomIdentity
import com.rodrigomachado.riff.core.room.RoomIdentityStore
import com.rodrigomachado.riff.core.room.RoomRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI
import kotlin.random.Random

/**
 * Uma escrita recebida pelo [FakeFirebase]: quem ([uid]), o método, o caminho (sem `/` nas pontas), o corpo e o
 * `if-match` (null sem ele).
 */
data class FirebaseWrite(
    val uid: String,
    val method: String,
    val path: String,
    val body: String?,
    val ifMatch: String? = null,
)

/** Um jogador semeado por [FakeFirebase.seedRoom] (tempos null = o [FakeFirebase.clock] de agora). */
data class FakePlayer(
    val uid: String,
    val name: String = uid,
    val device: RoomDevice = RoomDevice.PHONE,
    val joinedAt: Long? = null,
    val seenAt: Long? = null,
)

/**
 * O Firebase em memória para os testes (nenhum teste fala com o Firebase de verdade): o login anônimo do Google e o
 * Realtime Database pela API REST, com `GET`/`PUT`/`PATCH`/`DELETE`, o ETag (`X-Firebase-ETag`, `if-match`, 412),
 * o `{".sv":"timestamp"}` pelo [clock] e o streaming (SSE: um `put` da árvore inteira a cada mudança). **Sem as
 * regras** (essas são testadas no emulador, em `firebase/rules.test.mjs`); [refuse] simula uma recusa (401).
 *
 * Ele mesmo é o HTTP do jogador [uid]; [client] dá o de outro jogador no mesmo banco, e [repository] já monta o
 * `RoomRepository` de qualquer um deles.
 */
class FakeFirebase(val clock: () -> Long, val uid: String = "u1") : RoomHttp {
    private val lock = Any()
    private val root = MutableStateFlow<Any?>(null)
    private val connected = MutableStateFlow(true)
    private val log = mutableListOf<FirebaseWrite>()
    private val self = Client(uid)

    /** Sem internet: os pedidos lançam [NoConnection] e os streamings abertos caem. */
    var online: Boolean
        get() = connected.value
        set(value) {
            connected.value = value
        }

    /** Uma escrita para a qual dá true é recusada com 401 (como as regras fariam), sem mudar nada. */
    var refuse: (FirebaseWrite) -> Boolean = { false }

    /** Não null: o login anônimo (cadastro e renovação) responde isto em vez de entrar (Google fora do ar, etc.). */
    var loginReply: HttpReply? = null

    /** Todas as escritas recebidas, na ordem (as recusadas também). */
    val writes: List<FirebaseWrite> get() = synchronized(lock) { log.toList() }

    /** O HTTP do jogador [uid] (o login anônimo dá esse uid). */
    fun client(uid: String): RoomHttp = if (uid == this.uid) self else Client(uid)

    /**
     * O repositório do jogador [uid] neste banco. [deviceClock] é o relógio do aparelho (por padrão o mesmo do
     * servidor, o [clock]; diferente, simula um relógio errado).
     */
    fun repository(
        uid: String = this.uid,
        device: RoomDevice = RoomDevice.PHONE,
        random: Random = Random.Default,
        deviceClock: () -> Long = clock,
    ): RoomRepository {
        val http = client(uid)
        val auth = AnonymousAuth(http, MemoryIdentityStore(), API_KEY, deviceClock)
        return RoomRepository(FirebaseDb(http, auth, DB_URL), auth, device, deviceClock, random)
    }

    /** O valor em [path] (JSONObject, JSONArray, primitivo ou null). */
    fun get(path: String): Any? = valueAt(root.value, path)

    /** Grava [json] em [path] direto, sem passar pelo HTTP (o estado inicial do teste). */
    fun set(path: String, json: String) {
        synchronized(lock) { root.value = JsonTree.put(root.value, path, resolve(parse(json))) }
    }

    /** Uma sala no formato do app: os [players] nas vagas v1, v2… na ordem. */
    fun seedRoom(code: String, owner: String, players: List<FakePlayer>, activityAt: Long = clock()) {
        val now = clock()
        val list = JSONObject()
        val slots = JSONObject()
        players.forEachIndexed { index, player ->
            val slot = "v${index + 1}"
            slots.put(slot, player.uid)
            list.put(
                player.uid,
                JSONObject()
                    .put("nome", player.name)
                    .put("aparelho", if (player.device == RoomDevice.PC) "pc" else "celular")
                    .put("vaga", slot)
                    .put("entrouEm", player.joinedAt ?: now)
                    .put("vistoEm", player.seenAt ?: now),
            )
        }
        val room = JSONObject()
            .put("dono", owner)
            .put("estado", "sala")
            .put("criadaEm", activityAt)
            .put("atividadeEm", activityAt)
            .put("jogadores", list)
            .put("vagas", slots)
        set("salas/$code", room.toString())
    }

    override suspend fun request(method: String, url: String, body: String?, headers: Map<String, String>) =
        self.request(method, url, body, headers)

    override fun stream(url: String): Flow<String> = self.stream(url)

    private inner class Client(private val uid: String) : RoomHttp {
        override suspend fun request(
            method: String,
            url: String,
            body: String?,
            headers: Map<String, String>,
        ): HttpReply {
            if (!online) throw NoConnection()
            val login = url.startsWith(SIGN_UP_URL) || url.startsWith(REFRESH_URL)
            loginReply?.takeIf { login }?.let { return it }
            return when {
                url.startsWith(SIGN_UP_URL) -> reply(
                    JSONObject().put("idToken", "token-$uid").put("refreshToken", "refresh-$uid")
                        .put("expiresIn", "3600").put("localId", uid),
                )

                url.startsWith(REFRESH_URL) -> reply(
                    JSONObject().put("id_token", "token-$uid").put("refresh_token", "refresh-$uid")
                        .put("expires_in", "3600").put("user_id", uid),
                )

                url.startsWith(DB_URL) -> database(uid, method, pathOf(url), body, headers)

                else -> HttpReply(HTTP_NOT_FOUND, "", emptyMap())
            }
        }

        override fun stream(url: String): Flow<String> = flow {
            if (!online) throw NoConnection()
            val path = pathOf(url)
            combine(root, connected) { tree, up -> if (up) text(valueAt(tree, path)) else null }
                .distinctUntilChanged()
                .collect { value ->
                    if (value == null) throw NoConnection()
                    emit("event: put")
                    emit("data: {\"path\":\"/\",\"data\":$value}")
                    emit("")
                }
        }
    }

    private fun database(
        uid: String,
        method: String,
        path: String,
        body: String?,
        headers: Map<String, String>,
    ): HttpReply = synchronized(lock) {
        if (method == "GET") {
            val current = valueAt(root.value, path)
            val etag = if (headers[ETAG_HEADER] == "true") mapOf("etag" to etagOf(current)) else emptyMap()
            HttpReply(HTTP_OK, text(current), etag)
        } else {
            write(FirebaseWrite(uid, method, path, body, headers["if-match"]))
        }
    }

    private fun write(write: FirebaseWrite): HttpReply {
        log += write
        val current = valueAt(root.value, write.path)
        if (refuse(write)) return HttpReply(HTTP_UNAUTHORIZED, """{"error":"Permission denied"}""", emptyMap())
        if (write.ifMatch != null && write.ifMatch != etagOf(current)) {
            return HttpReply(HTTP_PRECONDITION_FAILED, text(current), mapOf("etag" to etagOf(current)))
        }
        val value = resolve(parse(write.body.orEmpty()))
        root.value = when (write.method) {
            "PUT" -> JsonTree.put(root.value, write.path, value)
            "PATCH" -> JsonTree.patch(root.value, write.path, value as? JSONObject ?: JSONObject())
            "DELETE" -> JsonTree.put(root.value, write.path, null)
            else -> return HttpReply(HTTP_BAD_METHOD, "", emptyMap())
        }
        return HttpReply(HTTP_OK, text(value), emptyMap())
    }

    /** O `{".sv":"timestamp"}` vira a hora do [clock], em qualquer profundidade. */
    private fun resolve(value: Any?): Any? = when {
        value is JSONObject && value.length() == 1 && value.opt(".sv") == "timestamp" -> clock()

        value is JSONObject -> JSONObject().also { copy ->
            value.keys().forEach { key -> copy.put(key, resolve(value.get(key)) ?: JSONObject.NULL) }
        }

        value is JSONArray -> JSONArray().also { copy ->
            for (i in 0 until value.length()) copy.put(resolve(value.get(i)) ?: JSONObject.NULL)
        }

        else -> value
    }

    companion object {
        /** O endereço do banco falso (o `dbUrl` do `FirebaseDb`). */
        const val DB_URL = "https://riff-falso.firebaseio.com"
        const val API_KEY = "chave-falsa"
        private const val SIGN_UP_URL = "https://identitytoolkit.googleapis.com/"
        private const val REFRESH_URL = "https://securetoken.googleapis.com/"
        private const val ETAG_HEADER = "X-Firebase-ETag"
        private const val HTTP_OK = 200
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_BAD_METHOD = 405
        private const val HTTP_PRECONDITION_FAILED = 412

        private fun reply(json: JSONObject) = HttpReply(HTTP_OK, json.toString(), emptyMap())

        private fun pathOf(url: String) = URI(url).path.removeSuffix(".json").trim('/')

        private fun parse(text: String): Any? =
            if (text.isBlank()) null else JSONTokener(text).nextValue().takeUnless { it == JSONObject.NULL }

        private fun valueAt(tree: Any?, path: String): Any? =
            path.split('/').filter { it.isNotEmpty() }.fold(tree) { node, key ->
                (node as? JSONObject)?.opt(key)?.takeUnless { it == JSONObject.NULL }
            }

        private fun text(value: Any?): String = when (value) {
            null -> "null"
            is String -> JSONObject.quote(value)
            else -> value.toString()
        }

        /** Como o do Firebase: muda quando o valor muda; `null_etag` para o que não existe. */
        private fun etagOf(value: Any?) = if (value == null) "null_etag" else "e" + text(value).hashCode()
    }
}

/** A identidade do login anônimo só na memória. */
class MemoryIdentityStore(private var identity: RoomIdentity? = null) : RoomIdentityStore {
    override suspend fun read(): RoomIdentity? = identity

    override suspend fun write(identity: RoomIdentity) {
        this.identity = identity
    }
}
