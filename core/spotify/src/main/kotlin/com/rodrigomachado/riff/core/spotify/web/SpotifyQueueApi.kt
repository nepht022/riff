package com.rodrigomachado.riff.core.spotify.web

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/** Uma música da fila do Spotify, com os artistas juntos por ", " como o Spotify mostra. */
data class QueuedTrack(val uri: String, val title: String, val artist: String, val durationMs: Long)

sealed interface QueueRead {
    data class Tracks(val tracks: List<QueuedTrack>) : QueueRead

    /** O Spotify pediu para esperar (429). */
    data class RetryAfter(val seconds: Long) : QueueRead

    /** Sem autorização, sem rede ou resposta inesperada: tenta na próxima leitura. */
    data object Unavailable : QueueRead
}

data class HttpAnswer(val code: Int, val body: String, val retryAfterSeconds: Long? = null)

/** GET com o token no cabeçalho `Authorization: Bearer`. */
fun interface AuthorizedGet {
    suspend fun get(url: String, token: String): HttpAnswer
}

/** As próximas músicas da fila do Spotify. */
interface SpotifyQueue {
    suspend fun next(count: Int = 2): QueueRead
}

/** As próximas músicas da fila do Spotify (`GET /v1/me/player/queue`); episódios e faixas locais ficam de fora. */
class SpotifyQueueApi(private val auth: SpotifyWebAuth, private val http: AuthorizedGet) : SpotifyQueue {
    @Inject constructor(auth: SpotifyWebAuth) : this(auth, UrlConnectionGet)

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // qualquer falha: tenta na próxima leitura
    override suspend fun next(count: Int): QueueRead {
        val token = auth.accessToken() ?: return QueueRead.Unavailable
        return try {
            var answer = http.get(QUEUE_URL, token)
            if (answer.code == HTTP_UNAUTHORIZED) {
                val renewed = auth.onUnauthorized() ?: return QueueRead.Unavailable
                answer = http.get(QUEUE_URL, renewed)
            }
            read(answer, count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "fila do Spotify: sem conexão")
            QueueRead.Unavailable
        }
    }

    private fun read(answer: HttpAnswer, count: Int): QueueRead = when (answer.code) {
        in HTTP_OK -> parse(answer.body, count)

        HTTP_TOO_MANY -> QueueRead.RetryAfter(answer.retryAfterSeconds ?: DEFAULT_RETRY_S)

        else -> {
            Log.w(TAG, "fila do Spotify: HTTP ${answer.code}")
            QueueRead.Unavailable
        }
    }

    @Suppress("SwallowedException")
    private fun parse(body: String, count: Int): QueueRead = try {
        val queue = JSONObject(body).optJSONArray("queue")
        val tracks = (0 until (queue?.length() ?: 0)).asSequence()
            .map { queue!!.getJSONObject(it) }
            .filter { it.optString("type") == "track" && !it.optBoolean("is_local") }
            .map { item ->
                val artists = item.getJSONArray("artists")
                QueuedTrack(
                    uri = item.getString("uri"),
                    title = item.getString("name"),
                    artist = (0 until artists.length()).joinToString(
                        ", ",
                    ) { artists.getJSONObject(it).getString("name") },
                    durationMs = item.optLong("duration_ms"),
                )
            }
            .take(count)
            .toList()
        QueueRead.Tracks(tracks)
    } catch (e: JSONException) {
        Log.w(TAG, "fila do Spotify: resposta inesperada")
        QueueRead.Unavailable
    }

    private companion object {
        const val TAG = "RiffQueue"
        const val QUEUE_URL = "https://api.spotify.com/v1/me/player/queue"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_TOO_MANY = 429
        const val DEFAULT_RETRY_S = 30L
        val HTTP_OK = 200..299
    }
}

/** GET pelo HTTP do próprio Android. */
object UrlConnectionGet : AuthorizedGet {
    private const val TIMEOUT_MS = 15_000
    private const val HTTP_ERROR = 400

    override suspend fun get(url: String, token: String): HttpAnswer = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val code = connection.responseCode
            val stream = if (code >= HTTP_ERROR) connection.errorStream else connection.inputStream
            val body = stream?.use { it.readBytes().decodeToString() } ?: ""
            HttpAnswer(code, body, connection.getHeaderField("Retry-After")?.toLongOrNull())
        } finally {
            connection.disconnect()
        }
    }
}
