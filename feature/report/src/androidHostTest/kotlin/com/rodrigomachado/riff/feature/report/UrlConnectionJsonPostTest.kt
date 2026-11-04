package com.rodrigomachado.riff.feature.report

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress

/**
 * O HTTP de verdade contra um servidor local que responde como o Apps Script: o POST recebe um redirecionamento
 * 302, e a resposta (`{"ok":true}`) vem num GET ao endereço novo. O relato tem que chegar uma vez só.
 */
class UrlConnectionJsonPostTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val posts = mutableListOf<String>()
    private var gets = 0

    init {
        server.createContext("/exec") { exchange ->
            posts += exchange.requestMethod + " " + exchange.requestBody.readBytes().decodeToString()
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${server.address.port}/echo")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/echo") { exchange ->
            gets++
            val body = """{"ok":true}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    @Test
    fun thePostArrivesOnceAndTheAnswerComesAfterTheRedirect() = runBlocking {
        val (code, body) = UrlConnectionJsonPost.post(
            "http://127.0.0.1:${server.address.port}/exec",
            """{"texto":"Oi"}""",
        )

        assertEquals(200, code)
        assertEquals("""{"ok":true}""", body)
        assertEquals(listOf("""POST {"texto":"Oi"}"""), posts)
        assertEquals(1, gets)
    }

    @Test
    fun noServerIsAConnectionError() {
        server.stop(0)

        assertThrows(IOException::class.java) {
            runBlocking { UrlConnectionJsonPost.post("http://127.0.0.1:${server.address.port}/exec", "{}") }
        }
    }
}
