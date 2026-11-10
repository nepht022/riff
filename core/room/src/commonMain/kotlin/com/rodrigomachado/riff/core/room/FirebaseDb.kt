package com.rodrigomachado.riff.core.room

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** O `{".sv":"timestamp"}`: o Firebase grava a hora dele no lugar. */
object ServerTime {
    val NOW: Map<String, String> = mapOf(".sv" to "timestamp")
}

sealed interface PutResult {
    data object Ok : PutResult

    /** 412: o `if-match` não bateu (com `null_etag`, o caminho já existe). */
    data object Conflict : PutResult
}

/**
 * O Realtime Database pela API REST: `{dbUrl}/{caminho}.json?auth={idToken}`. Os valores lidos são JSONObject,
 * JSONArray, primitivos ou null; os escritos podem ser também Map e List (um null dentro de um Map do [patch] apaga
 * aquele filho). Sem rede, [NoConnection]; recusado pelas regras (401/403), resposta ilegível ou outro erro,
 * [RoomHttpError] (nenhuma outra exceção sai daqui, nem do login: ver [AnonymousAuth]).
 */
class FirebaseDb(private val http: RoomHttp, private val auth: AnonymousAuth, dbUrl: String) {
    private val base = dbUrl.trimEnd('/')

    suspend fun get(path: String): Any? = call("GET", path, null).let { read(it) }

    suspend fun put(path: String, value: Any?, ifMatch: String? = null): PutResult {
        val headers = if (ifMatch == null) emptyMap() else mapOf(ETAG_HEADER to "true", "if-match" to ifMatch)
        val reply = http.request("PUT", url(path), toJsonText(value), headers)
        return when {
            reply.code == HTTP_PRECONDITION_FAILED -> PutResult.Conflict
            reply.code in HTTP_OK -> PutResult.Ok
            else -> throw RoomHttpError(reply.code)
        }
    }

    suspend fun patch(path: String, values: Map<String, Any?>) {
        call("PATCH", path, toJsonText(values))
    }

    suspend fun delete(path: String) {
        call("DELETE", path, null)
    }

    /** O ETag do caminho (para um [put] com `ifMatch`) e o valor de agora. */
    suspend fun etag(path: String): Pair<String, Any?> {
        val reply = call("GET", path, null, mapOf(ETAG_HEADER to "true"))
        return reply.headers["etag"].orEmpty() to read(reply)
    }

    /** O valor da resposta; uma resposta ilegível (cortada, um proxy) é [RoomHttpError]. */
    private fun read(reply: HttpReply): Any? = try {
        parseJson(reply.body)
    } catch (_: JSONException) {
        throw RoomHttpError(reply.code)
    }

    /**
     * A árvore inteira do [path] a cada `put`/`patch` do streaming (null = não existe). `cancel`/`auth_revoked`:
     * renova o token e reabre na hora. Queda, erro ou o servidor fechando: reabre depois de 1 s, 2 s, 4 s… até 30 s
     * (volta a 1 s quando chega um evento).
     */
    fun observe(path: String): Flow<Any?> = flow {
        var wait = FIRST_WAIT_MS
        var renewToken = false
        while (true) {
            val outcome = try {
                if (renewToken) auth.refreshNow()
                renewToken = false
                listen(path) { wait = FIRST_WAIT_MS }
            } catch (_: NoConnection) {
                Outcome.DROPPED
            } catch (e: RoomHttpError) {
                // 401 ao abrir: o token não vale mais; o Google fora do ar ou outro erro: só espera
                renewToken = renewToken || e.code == HTTP_UNAUTHORIZED
                Outcome.DROPPED
            } catch (_: JSONException) {
                Outcome.DROPPED // evento ilegível: a próxima conexão manda a árvore inteira de novo
            }
            if (outcome == Outcome.REVOKED) {
                renewToken = true
            } else {
                delay(wait)
                wait = (wait * 2).coerceAtMost(MAX_WAIT_MS)
            }
        }
    }

    private enum class Outcome { REVOKED, DROPPED }

    /** Uma conexão do streaming, até cair ([Outcome.DROPPED]) ou o token não valer mais ([Outcome.REVOKED]). */
    private suspend fun FlowCollector<Any?>.listen(path: String, onEvent: () -> Unit): Outcome {
        val parser = SseParser()
        var tree: Any? = null
        var revoked = false
        http.stream(url(path)).takeWhile { line ->
            val event = parser.feed(line) ?: return@takeWhile true
            when (event.type) {
                SseParser.PUT, SseParser.PATCH -> {
                    tree = if (event.type == SseParser.PUT) {
                        JsonTree.put(tree, event.path.orEmpty(), event.data)
                    } else {
                        JsonTree.patch(tree, event.path.orEmpty(), event.data as? JSONObject ?: JSONObject())
                    }
                    onEvent()
                    emit(tree)
                }

                SseParser.CANCEL, SseParser.AUTH_REVOKED -> revoked = true
            }
            !revoked
        }.collect()
        return if (revoked) Outcome.REVOKED else Outcome.DROPPED
    }

    private suspend fun call(
        method: String,
        path: String,
        body: String?,
        headers: Map<String, String> = emptyMap(),
    ): HttpReply {
        val reply = http.request(method, url(path), body, headers)
        if (reply.code !in HTTP_OK) throw RoomHttpError(reply.code)
        return reply
    }

    private suspend fun url(path: String) = "$base/${path.trim('/')}.json?auth=${auth.token()}"

    private companion object {
        const val ETAG_HEADER = "X-Firebase-ETag"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_PRECONDITION_FAILED = 412
        const val FIRST_WAIT_MS = 1_000L
        const val MAX_WAIT_MS = 30_000L
        val HTTP_OK = 200..299
    }
}

/** O texto JSON de um valor: Map e List viram objeto e lista, null vira `null` (também dentro deles). */
internal fun toJsonText(value: Any?): String = when (val json = toJson(value)) {
    JSONObject.NULL -> "null"
    is String -> JSONObject.quote(json)
    else -> json.toString()
}

private fun toJson(value: Any?): Any = when (value) {
    null -> JSONObject.NULL

    is Map<*, *> -> JSONObject().also { obj ->
        value.forEach { (key, child) -> obj.put(key.toString(), toJson(child)) }
    }

    is Iterable<*> -> JSONArray().also { array -> value.forEach { array.put(toJson(it)) } }

    else -> value
}
