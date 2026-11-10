package com.rodrigomachado.riff.core.room

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** O HTTP do Java do PC contra um servidor local que imita o Firebase (nada sai da máquina). */
class UrlConnectionRoomHttpDesktopTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val received = mutableListOf<String>()
    private val streamClosed = CountDownLatch(1)
    private val releaseSilent = CountDownLatch(1)
    private val base get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/salas/ABCD.json") { exchange ->
            val override = exchange.requestHeaders.getFirst("X-HTTP-Method-Override")
            val ifMatch = exchange.requestHeaders.getFirst("if-match")
            received += "${exchange.requestMethod} ${override ?: "-"} $ifMatch " +
                exchange.requestBody.readBytes().decodeToString()
            exchange.responseHeaders.add("ETag", "abc=")
            exchange.reply(if (exchange.requestMethod == "PUT") 412 else 200, """{"dono":"u1"}""")
        }
        server.createContext("/stream.json") { exchange ->
            received += exchange.requestHeaders.getFirst("Accept")
            exchange.sendResponseHeaders(200, 0)
            val out = exchange.responseBody
            out.write("event: put\ndata: {\"path\":\"/\",\"data\":1}\n\n".toByteArray())
            out.flush()
            // segura a conexão aberta, como o Firebase, até o app desistir
            try {
                repeat(50) {
                    Thread.sleep(100)
                    out.write(":\n".toByteArray())
                    out.flush()
                }
            } catch (_: java.io.IOException) {
                streamClosed.countDown()
            }
            exchange.close()
        }
        server.createContext("/calado.json") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.write("event: put\ndata: {\"path\":\"/\",\"data\":1}\n\n".toByteArray())
            exchange.responseBody.flush()
            // calado depois do primeiro evento (nem keep-alive), até o fim do teste
            releaseSilent.await(SILENT_S, TimeUnit.SECONDS)
            exchange.close()
        }
        server.createContext("/negado.json") { it.reply(401, "{}") }
        // uma thread por conexão: o streaming calado não segura os outros pedidos
        server.executor = Executors.newCachedThreadPool()
        server.start()
    }

    private fun HttpExchange.reply(code: Int, body: String) {
        val bytes = body.toByteArray()
        sendResponseHeaders(code, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    @After
    fun stop() {
        releaseSilent.countDown()
        server.stop(0)
    }

    @Test
    fun requestsCarryMethodHeadersAndBody() = runBlocking {
        val get = UrlConnectionRoomHttp.request("GET", "$base/salas/ABCD.json", null, emptyMap())
        val put = UrlConnectionRoomHttp.request("PUT", "$base/salas/ABCD.json", "{}", mapOf("if-match" to "null_etag"))
        // o HttpURLConnection não sabe PATCH: vai como POST com o X-HTTP-Method-Override, que o Firebase aceita
        val patch = UrlConnectionRoomHttp.request("PATCH", "$base/salas/ABCD.json", """{"a":1}""", emptyMap())

        assertEquals(200, get.code)
        assertEquals("""{"dono":"u1"}""", get.body)
        assertEquals("abc=", get.headers["etag"])
        assertEquals(412, put.code)
        assertEquals(200, patch.code)
        assertEquals(
            listOf("GET - null ", "PUT - null_etag {}", """POST PATCH null {"a":1}"""),
            received,
        )
    }

    @Test
    fun streamGivesLinesAndClosesWhenCancelled() = runBlocking {
        val lines = withTimeout(5_000) { UrlConnectionRoomHttp.stream("$base/stream.json").take(2).toList() }

        assertEquals(listOf("event: put", """data: {"path":"/","data":1}"""), lines)
        assertEquals(listOf("text/event-stream"), received)
        // cancelar a coleta fecha a conexão (o servidor percebe ao escrever o próximo keep-alive)
        assertEquals(true, streamClosed.await(5, TimeUnit.SECONDS))
    }

    /**
     * Sair da sala no PC: a coleta é cancelada na thread da janela com a leitura parada (o servidor calado). O
     * cancelamento não pode prender a janela nem esperar a leitura acabar.
     */
    @Test
    fun cancellingASilentStreamDoesNotBlockTheWindowThread() = runBlocking {
        val window = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            val firstLine = CompletableDeferred<Unit>()
            val job = launch(window) {
                UrlConnectionRoomHttp.stream("$base/calado.json").onEach { firstLine.complete(Unit) }.collect()
            }
            withTimeout(5_000) { firstLine.await() }
            Thread.sleep(200) // a leitura já parada esperando o servidor

            val start = System.nanoTime()
            withContext(window) { job.cancel() }
            withTimeout(5_000) { withContext(window) { } } // a janela livre de novo
            val windowFreeMs = (System.nanoTime() - start) / NANOS_PER_MS
            withTimeout(5_000) { job.join() }
            val cancelledMs = (System.nanoTime() - start) / NANOS_PER_MS

            assertTrue("a janela ficou presa $windowFreeMs ms", windowFreeMs < 1_000)
            assertTrue("o cancelamento levou $cancelledMs ms", cancelledMs < 1_000)
        } finally {
            window.close()
        }
    }

    @Test
    fun streamRefusedThrowsTheCode() = runBlocking {
        try {
            UrlConnectionRoomHttp.stream("$base/negado.json").first()
            fail("o banco recusou")
        } catch (e: RoomHttpError) {
            assertEquals(401, e.code)
        }
    }

    @Test
    fun nobodyListeningIsNoConnection() = runBlocking {
        val closedPort = ServerSocket(0).use { it.localPort }
        try {
            UrlConnectionRoomHttp.request("GET", "http://127.0.0.1:$closedPort/x.json", null, emptyMap())
            fail("ninguém ouvindo")
        } catch (_: NoConnection) {
            // esperado
        }
        try {
            UrlConnectionRoomHttp.stream("http://127.0.0.1:$closedPort/x.json").first()
            fail("ninguém ouvindo")
        } catch (_: NoConnection) {
            // esperado
        }
    }

    private companion object {
        const val SILENT_S = 10L
        const val NANOS_PER_MS = 1_000_000L
    }
}
