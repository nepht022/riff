package com.rodrigomachado.riff.core.encore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class HttpResponse(val code: Int, val length: Long?, val body: InputStream) : Closeable {
    override fun close() = body.close()
}

interface HttpTransport {
    suspend fun post(url: String, json: String): HttpResponse

    suspend fun get(url: String, range: LongRange? = null): HttpResponse
}

/** HTTP do próprio Android (sem biblioteca nova). */
class UrlConnectionTransport : HttpTransport {
    override suspend fun post(url: String, json: String) = withContext(Dispatchers.IO) {
        val connection = connect(url).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { it.write(json.toByteArray()) }
        response(connection)
    }

    override suspend fun get(url: String, range: LongRange?) = withContext(Dispatchers.IO) {
        val connection = connect(url)
        range?.let { connection.setRequestProperty("Range", "bytes=${it.first}-${it.last}") }
        response(connection)
    }

    private fun connect(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
    }

    private fun response(connection: HttpURLConnection): HttpResponse {
        val code = connection.responseCode
        val body = if (code >= 400) connection.errorStream ?: "".byteInputStream() else connection.inputStream
        return HttpResponse(code, connection.contentLengthLong.takeIf { it >= 0 }, body)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
    }
}
