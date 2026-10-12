package com.rodrigomachado.riff.core.encore

import com.rodrigomachado.riff.core.chart.ChartAudioFiles
import com.rodrigomachado.riff.core.chart.sng.SngIndex
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream

data class EncoreChart(
    val md5: String,
    val name: String,
    val artist: String,
    val charter: String?,
    val durationMs: Long?,
    val guitarDifficulties: Set<Difficulty>,
    val hasVideoBackground: Boolean,
) {
    /** Sem o vídeo de fundo (o Riff não usa) quando o mapa tem um. */
    val downloadUrl: String get() = "$FILES/$md5${if (hasVideoBackground) "_novideo" else ""}.sng"
}

data class EncorePage(val charts: List<EncoreChart>, val found: Int, val page: Int) {
    val hasMore: Boolean get() = page * PER_PAGE < found
}

class EncoreUnavailableException(cause: Throwable?) : IOException("Encore indisponível", cause)

class EncoreFormatException(message: String) : IOException(message)

/** Pesquisa e downloads do Encore (enchor.us). A API não é documentada: o formato mudou → [EncoreFormatException]. */
class EncoreClient(private val transport: HttpTransport, private val io: CoroutineDispatcher = Dispatchers.IO) {
    // Tudo em IO: ler o corpo da resposta é rede, e o Android proíbe rede na thread da tela.
    suspend fun search(query: String, page: Int): EncorePage = withContext(io) {
        val body = JSONObject()
            .put("search", query).put("per_page", PER_PAGE).put("page", page).put("instrument", "guitar")
            .put("difficulty", JSONObject.NULL).put("drumType", JSONObject.NULL).put("drumsReviewed", false)
            .put("sort", JSONObject.NULL).put("source", "api")
        val text = call { transport.post("$API/search", body.toString()) }.use { response ->
            if (response.code !in 200..299) throw EncoreUnavailableException(IOException("HTTP ${response.code}"))
            response.body.readBytes().decodeToString()
        }
        parse(text, page)
    }

    /** Pelo começo do pacote (o índice); null se não deu para saber (a marca só fica de fora). */
    suspend fun hasSeparateGuitar(chart: EncoreChart): Boolean? = withContext(io) { peek(chart) }

    @Suppress("SwallowedException")
    private suspend fun peek(chart: EncoreChart): Boolean? = try {
        call { transport.get(chart.downloadUrl, 0L until HEAD_BYTES) }.use { response ->
            if (response.code !in 200..299) return null
            when (val index = SngIndex.parse(response.body.readBytes())) {
                is SngIndex.Result.Complete -> ChartAudioFiles.hasSeparateGuitar(index.files.map { it.name })
                is SngIndex.Result.NeedsMore -> null
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        null
    }

    suspend fun download(chart: EncoreChart, into: OutputStream, onProgress: (Float) -> Unit) = withContext(io) {
        call { transport.get(chart.downloadUrl) }.use { response ->
            if (response.code !in 200..299) throw EncoreUnavailableException(IOException("HTTP ${response.code}"))
            val buffer = ByteArray(BUFFER_BYTES)
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = response.body.read(buffer)
                if (read < 0) break
                into.write(buffer, 0, read)
                total += read
                response.length?.let { onProgress((total.toFloat() / it).coerceAtMost(1f)) }
            }
            onProgress(1f)
        }
    }

    private suspend fun call(request: suspend () -> HttpResponse): HttpResponse = try {
        request()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        throw EncoreUnavailableException(e)
    }

    private fun parse(text: String, page: Int): EncorePage = try {
        val root = JSONObject(text)
        val data = root.getJSONArray("data")
        val charts = (0 until data.length()).map { data.getJSONObject(it) }
            .filterNot { OfficialCharts.isOfficial(it.optStringOrNull("charter"), it.optStringOrNull("icon")) }
            .filter { !it.isNull("diff_guitar") }
            .map(::chartOf)
            .distinctBy { it.md5 }
        EncorePage(charts, root.getInt("found"), page)
    } catch (e: JSONException) {
        throw EncoreFormatException("resposta inesperada do Encore: ${e.message}").apply { initCause(e) }
    }

    private fun chartOf(json: JSONObject): EncoreChart {
        val counts = json.optJSONObject("notesData")?.optJSONArray("noteCounts")
        val difficulties = (0 until (counts?.length() ?: 0)).map { counts!!.getJSONObject(it) }
            .filter { it.optString("instrument") == "guitar" }
            .mapNotNull { count ->
                Difficulty.entries.firstOrNull {
                    it.name.equals(
                        count.optString("difficulty"),
                        true,
                    )
                }
            }
            .toSet()
        return EncoreChart(
            md5 = json.getString("md5"),
            name = json.optStringOrNull("name") ?: "?",
            artist = json.optStringOrNull("artist").orEmpty(),
            charter = json.optStringOrNull("charter"),
            durationMs = json.optLong("song_length").takeIf { it > 0 },
            guitarDifficulties = difficulties,
            hasVideoBackground = json.optBoolean("hasVideoBackground"),
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}

internal const val PER_PAGE = 25
private const val API = "https://api.enchor.us"
private const val FILES = "https://files.enchor.us"
private const val HEAD_BYTES = 16_384L
private const val BUFFER_BYTES = 64 * 1024
