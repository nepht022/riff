package com.rodrigomachado.riff.core.room

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Um pedido recebido pelo [FakeHttp]. */
data class FakeRequest(val method: String, val url: String, val body: String?, val headers: Map<String, String>)

/**
 * HTTP falso: nenhum teste fala com o Google ou o Firebase. [answer] responde os pedidos; cada [stream] aberto
 * consome a próxima "conexão" de [streams] (linhas e, no fim, uma queda ou o fim normal), e depois dela fica parado.
 */
class FakeHttp(var answer: (FakeRequest) -> HttpReply = { HttpReply(200, "null", emptyMap()) }) : RoomHttp {
    val requests = mutableListOf<FakeRequest>()
    val streamUrls = mutableListOf<String>()
    val streams = ArrayDeque<Flow<String>>()

    override suspend fun request(method: String, url: String, body: String?, headers: Map<String, String>): HttpReply {
        val request = FakeRequest(method, url, body, headers)
        requests += request
        return answer(request)
    }

    override fun stream(url: String): Flow<String> = flow {
        streamUrls += url
        val next = streams.removeFirstOrNull()
        if (next == null) awaitCancellation() else emitAll(next)
    }
}

/** Identidade guardada em memória. */
class FakeIdentityStore(var identity: RoomIdentity? = null) : RoomIdentityStore {
    var writes = 0

    override suspend fun read(): RoomIdentity? = identity

    override suspend fun write(identity: RoomIdentity) {
        writes++
        this.identity = identity
    }
}

fun ok(body: String, headers: Map<String, String> = emptyMap()) = HttpReply(200, body, headers)

/** As linhas de um evento SSE do Firebase (a linha vazia no fim fecha o evento). */
fun sse(event: String, data: String) = listOf("event: $event", "data: $data", "")
