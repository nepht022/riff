package com.rodrigomachado.riff.feature.search

import com.rodrigomachado.riff.core.chart.sng.SngWriter
import com.rodrigomachado.riff.core.data.EncoreDownloads
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.download.ChartDownloader
import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.encore.HttpResponse
import com.rodrigomachado.riff.core.encore.HttpTransport
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject
import java.io.IOException

class FakeTransport : HttpTransport {
    var postBody: String? = null

    /** Corpo de cada página (a página pedida vem no JSON); vazio usa [postBody]. */
    var pages: List<String> = emptyList()

    /** md5 dos pacotes com guitarra separada; os outros só têm song.opus. */
    var stems: Set<String> = emptySet()
    var failure: IOException? = null
    var failFromPage: Int? = null
    var rangeBody: ByteArray? = null
    var peekGate: CompletableDeferred<Unit>? = null
    var searchGate: CompletableDeferred<Unit>? = null
    var lastJson: String? = null
    var searches = 0
    var peeks = 0

    override suspend fun post(url: String, json: String): HttpResponse {
        searchGate?.await()
        failure?.let { throw it }
        val page = JSONObject(json).getInt("page")
        failFromPage?.let { if (page >= it) throw java.net.UnknownHostException("api.enchor.us") }
        lastJson = json
        searches++
        val body = if (pages.isEmpty()) checkNotNull(postBody) else pages.getOrElse(page - 1) { EMPTY_PAGE }
        return HttpResponse(201, null, body.byteInputStream())
    }

    override suspend fun get(url: String, range: LongRange?): HttpResponse {
        peeks++
        peekGate?.await()
        rangeBody?.let { return HttpResponse(206, null, it.inputStream()) }
        if (pages.isEmpty()) return HttpResponse(404, null, "".byteInputStream())
        val md5 = url.substringAfterLast('/').substringBefore('.').substringBefore('_')
        val files = if (md5 in stems) {
            mapOf(
                "guitar.ogg" to ByteArray(4),
                "song.ogg" to ByteArray(4),
            )
        } else {
            mapOf("song.opus" to ByteArray(4))
        }
        return HttpResponse(206, null, SngWriter.write(mapOf("name" to md5), files).inputStream())
    }

    private companion object {
        const val EMPTY_PAGE = """{"found": 0, "out_of": 0, "page": 1, "data": []}"""
    }
}

private val STEMS = mapOf("guitar.ogg" to ByteArray(4), "song.ogg" to ByteArray(4))
private val SINGLE = mapOf("song.opus" to ByteArray(4))

/** Um resultado do Encore para as páginas falsas. */
fun c(md5: String, name: String) = md5 to name

/** Página no formato da resposta do Encore, com mapas de guitarra (Expert) da comunidade. */
fun page(vararg charts: Pair<String, String>, found: Int): String {
    val data = charts.joinToString(",") { (md5, name) ->
        """{"name": "$name", "artist": "Banda", "charter": "charter", "icon": "", "diff_guitar": 2,
            "md5": "$md5", "hasVideoBackground": false, "song_length": 200000,
            "notesData": {"noteCounts": [{"instrument": "guitar", "difficulty": "expert", "count": 100}]}}"""
    }
    return """{"found": $found, "out_of": $found, "page": 1, "data": [$data]}"""
}

class FakeDownloader : ChartDownloader {
    var progressSteps = emptyList<Float>()
    var gate: CompletableDeferred<Unit>? = null
    var calls = 0
    override suspend fun download(chart: EncoreChart, onProgress: (Float) -> Unit): Chart {
        calls++
        progressSteps.forEach(onProgress)
        gate?.await()
        val id = "baixado-${chart.md5}"
        return Chart(id, chart.name, chart.artist, null, null, null, "u", ChartFormat.CHART, setOf(Difficulty.EXPERT))
    }
}

class FakeEncoreDownloads : EncoreDownloads {
    val owned = mutableMapOf<String, String>()
    override val md5s get() = flowOf(owned.keys.toSet())

    override suspend fun remember(chartId: String, md5: String) {
        owned[md5] = chartId
    }

    override suspend fun chartIdOf(md5: String) = owned[md5]
}

class FakeFolderAccess : FolderAccess {
    var writable = true
    val persisted = mutableListOf<String>()

    override fun persistAccess(folderUri: String) {
        persisted += folderUri
    }

    override fun canWrite(folderUri: String) = writable
}

class FakeFolderStore : LibraryFolderStore {
    override val rootUri = MutableStateFlow<String?>("root")

    override suspend fun setRootUri(uri: String) {
        rootUri.value = uri
    }
}
