package com.rodrigomachado.riff.feature.report

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetSocketAddress

/**
 * O HTTP do Java do PC contra dois servidores locais que imitam o Apps Script: o POST em `/exec` recebe um 302 para
 * outro endereço (outra porta, como `script.googleusercontent.com` é outro host), onde um GET devolve `{"ok":true}`.
 * O relato chega uma vez só, e a resposta do segundo endereço é a que vale.
 */
class UrlConnectionJsonPostDesktopTest {
    private val script = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val echo = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val posts = mutableListOf<String>()
    private val echoed = mutableListOf<String>()

    init {
        script.createContext("/exec") { exchange ->
            posts += exchange.requestMethod + " " + exchange.requestBody.readBytes().decodeToString()
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${echo.address.port}/macros/echo?user=1")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        echo.createContext("/macros/echo") { exchange ->
            echoed += exchange.requestMethod + " " + exchange.requestURI.query
            val body = """{"ok":true}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        script.start()
        echo.start()
    }

    @After
    fun stop() {
        script.stop(0)
        echo.stop(0)
    }

    @Test
    fun redirectIsFollowedOnThePc() = runBlocking {
        val (code, body) = UrlConnectionJsonPost.post(
            "http://127.0.0.1:${script.address.port}/exec",
            """{"texto":"Oi"}""",
        )

        assertEquals(200, code)
        assertEquals("""{"ok":true}""", body)
        assertEquals(listOf("""POST {"texto":"Oi"}"""), posts)
        assertEquals(listOf("GET user=1"), echoed)
    }
}
