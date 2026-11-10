package com.rodrigomachado.riff.core.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** Resposta HTTP: os nomes dos cabeçalhos vêm em minúsculas (`etag`). */
data class HttpReply(val code: Int, val body: String, val headers: Map<String, String>)

/** Sem internet, servidor fora do alcance ou tempo esgotado. */
class NoConnection(cause: Throwable? = null) : Exception(cause)

/** O Firebase ou o Google responderam com um erro ([code]: 401/403 é recusa das regras ou do token). */
class RoomHttpError(val code: Int) : Exception("HTTP $code")

/** O HTTP da Sala: pedidos comuns e o streaming (SSE), linha a linha. */
interface RoomHttp {
    /** Lança [NoConnection] sem rede; qualquer código HTTP volta na resposta. */
    suspend fun request(method: String, url: String, body: String?, headers: Map<String, String>): HttpReply

    /**
     * As linhas de um `GET` com `Accept: text/event-stream`, enquanto a conexão durar (o fim do fluxo é o servidor
     * fechando). Lança [NoConnection] na queda e [RoomHttpError] se a resposta não for 200.
     */
    fun stream(url: String): Flow<String>
}

/** HTTP do Java, fora da thread da tela (celular e PC), como o do ranking. */
object UrlConnectionRoomHttp : RoomHttp {
    private const val TIMEOUT_MS = 15_000

    // O Firebase manda um keep-alive a cada ~30 s: 60 s sem nada é conexão morta.
    private const val STREAM_READ_TIMEOUT_MS = 60_000
    private const val HTTP_OK = 200
    private const val HTTP_ERROR = 400

    override suspend fun request(method: String, url: String, body: String?, headers: Map<String, String>): HttpReply =
        withContext(Dispatchers.IO) {
            try {
                val connection = open(url, TIMEOUT_MS)
                try {
                    // O HttpURLConnection não sabe PATCH: o Firebase aceita um POST com o método no cabeçalho.
                    if (method == "PATCH") {
                        connection.requestMethod = "POST"
                        connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
                    } else {
                        connection.requestMethod = method
                    }
                    if (body != null) connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                    if (body != null) {
                        connection.doOutput = true
                        connection.outputStream.use { it.write(body.toByteArray()) }
                    }
                    val code = connection.responseCode
                    val stream = if (code >= HTTP_ERROR) connection.errorStream else connection.inputStream
                    val text = stream?.use { it.readBytes().decodeToString() }.orEmpty()
                    val replyHeaders = connection.headerFields.entries
                        .filter { it.key != null && it.value.isNotEmpty() }
                        .associate { it.key.lowercase() to it.value.first() }
                    HttpReply(code, text, replyHeaders)
                } finally {
                    connection.disconnect()
                }
            } catch (e: IOException) {
                throw NoConnection(e)
            }
        }

    // As leituras dos streamings, fora do escopo de quem coleta (ver [stream]).
    private val readers = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * A leitura bloqueia a thread e o `disconnect()` do Java espera a leitura em andamento (o `close` do stream
     * "chunked" é sincronizado com o `read`): com o servidor calado, até o tempo esgotado. Por isso a leitura roda
     * solta, em [readers], e cancelar a coleta só pede o `disconnect()` numa thread de fundo, sem esperar: quem
     * cancela (a thread da janela no PC) fica livre na hora, e a thread da leitura termina quando o servidor mandar
     * a próxima linha (o keep-alive do Firebase, a cada ~30 s) ou o tempo esgotar.
     */
    override fun stream(url: String): Flow<String> = channelFlow {
        val connection = try {
            open(url, STREAM_READ_TIMEOUT_MS).apply { setRequestProperty("Accept", "text/event-stream") }
        } catch (e: IOException) {
            throw NoConnection(e)
        }
        readers.launch {
            try {
                val code = connection.responseCode
                if (code != HTTP_OK) throw RoomHttpError(code)
                connection.inputStream.bufferedReader().useLines { lines -> lines.forEach { send(it) } }
                channel.close()
            } catch (e: IOException) {
                channel.close(NoConnection(e))
            } catch (e: RoomHttpError) {
                channel.close(e)
            }
        }
        awaitClose { readers.launch { connection.disconnect() } }
    }.flowOn(Dispatchers.IO)

    private fun open(url: String, readTimeoutMs: Int) = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = readTimeoutMs
    }
}
