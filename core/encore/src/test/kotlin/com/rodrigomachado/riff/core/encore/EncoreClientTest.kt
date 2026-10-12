package com.rodrigomachado.riff.core.encore

import com.rodrigomachado.riff.core.chart.sng.SngWriter
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Transporte falso: guarda o que foi pedido e devolve respostas montadas no teste. */
class FakeTransport : HttpTransport {
    var post: HttpResponse? = null
    var onGet: (String, LongRange?) -> HttpResponse = { _, _ -> error("sem resposta") }
    var failure: IOException? = null
    var lastJson: String? = null
    var lastUrl: String? = null
    var lastRange: LongRange? = null

    override suspend fun post(url: String, json: String): HttpResponse {
        failure?.let { throw it }
        lastUrl = url
        lastJson = json
        return checkNotNull(post)
    }

    override suspend fun get(url: String, range: LongRange?): HttpResponse {
        failure?.let { throw it }
        lastUrl = url
        lastRange = range
        return onGet(url, range)
    }
}

@RunWith(RobolectricTestRunner::class)
class EncoreClientTest {
    private val fixture = checkNotNull(javaClass.getResource("/encore/search_teen_spirit.json")).readText()
    private val transport = FakeTransport()
    private val client = EncoreClient(transport)

    private fun chart(md5: String, video: Boolean = false) =
        EncoreChart(md5, "Música", "Banda", "Charter", 200_000, setOf(Difficulty.EXPERT), video)

    @Test
    fun searchSendsTheQueryAsAnApiClientAndKeepsOnlyCommunityCharts() = runTest {
        transport.post = HttpResponse(201, null, fixture.byteInputStream())

        val page = client.search("smells like teen spirit", page = 1)

        val sent = JSONObject(checkNotNull(transport.lastJson))
        assertEquals("smells like teen spirit", sent.getString("search"))
        assertEquals("api", sent.getString("source"))
        assertEquals("guitar", sent.getString("instrument"))
        // Harmonix (rb2dlc) e Neversoft (gh5) saem; os dois da comunidade ficam.
        assertEquals(
            listOf("a07d209baccd2d58042d81b869a0077e", "80dc4fff35cc77abec7edeb59de1580c"),
            page.charts.map { it.md5 },
        )
        assertEquals(setOf(Difficulty.EXPERT), page.charts.first().guitarDifficulties)
        assertEquals(
            "https://files.enchor.us/a07d209baccd2d58042d81b869a0077e.sng",
            page.charts.first().downloadUrl,
        )
    }

    @Test
    fun theSameChartListedTwiceAppearsOnce() = runTest {
        val twice = JSONObject(fixture).also { o -> o.getJSONArray("data").put(o.getJSONArray("data").get(0)) }
        transport.post = HttpResponse(201, null, twice.toString().byteInputStream())

        assertEquals(2, client.search("x", 1).charts.size)
    }

    @Test
    fun aChangedResponseIsReportedAsAFormatChange() {
        transport.post = HttpResponse(201, null, """{"results": []}""".byteInputStream())
        assertThrows(EncoreFormatException::class.java) { runBlocking { client.search("x", 1) } }
    }

    @Test
    fun aServerErrorOrNoNetworkIsUnavailable() {
        transport.post = HttpResponse(503, null, "".byteInputStream())
        assertThrows(EncoreUnavailableException::class.java) { runBlocking { client.search("x", 1) } }
        transport.failure = java.net.UnknownHostException("api.enchor.us")
        assertThrows(EncoreUnavailableException::class.java) { runBlocking { client.search("x", 1) } }
    }

    @Test
    fun theSeparateGuitarMarkComesFromTheBeginningOfThePackage() = runTest {
        val sng = SngWriter.write(
            mapOf("name" to "M"),
            mapOf("notes.chart" to ByteArray(10), "guitar.ogg" to ByteArray(10), "song.ogg" to ByteArray(10)),
        )
        transport.onGet = { _, range ->
            val end = (checkNotNull(range).last + 1).toInt().coerceAtMost(sng.size)
            HttpResponse(206, null, sng.copyOf(end).inputStream())
        }

        assertEquals(true, client.hasSeparateGuitar(chart("a07d209baccd2d58042d81b869a0077e")))
        assertEquals(0L..16_383L, transport.lastRange)
    }

    @Test
    fun theVideoFreePackageIsDownloadedWhenTheChartHasAVideo() = runTest {
        transport.onGet = { _, _ -> HttpResponse(200, 4, "abcd".byteInputStream()) }
        val out = ByteArrayOutputStream()
        val progress = mutableListOf<Float>()

        client.download(chart("a07d209baccd2d58042d81b869a0077e", video = true), out) { progress += it }

        assertEquals("https://files.enchor.us/a07d209baccd2d58042d81b869a0077e_novideo.sng", transport.lastUrl)
        assertEquals("abcd", out.toString())
        assertEquals(1f, progress.last())
    }

    @Test
    fun aConnectionThatDropsWhileTheAnswerArrivesIsUnavailable() {
        val dropping = object : java.io.InputStream() {
            override fun read(): Int = throw java.net.SocketTimeoutException("caiu no meio")
        }
        transport.post = HttpResponse(201, null, dropping)

        assertThrows(EncoreUnavailableException::class.java) { runBlocking { client.search("x", 1) } }
    }
}
