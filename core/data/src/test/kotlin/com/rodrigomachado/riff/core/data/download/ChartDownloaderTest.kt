package com.rodrigomachado.riff.core.data.download

import com.rodrigomachado.riff.core.chart.sng.ByteArrayRandomRead
import com.rodrigomachado.riff.core.chart.sng.SngWriter
import com.rodrigomachado.riff.core.data.EncoreDownloads
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.TrackMatchRepository
import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.data.folder.FolderWriter
import com.rodrigomachado.riff.core.data.folder.SngFolderSource
import com.rodrigomachado.riff.core.data.importer.FakeChartRepository
import com.rodrigomachado.riff.core.data.importer.FakeFolderSource
import com.rodrigomachado.riff.core.data.importer.FolderChartImporter
import com.rodrigomachado.riff.core.data.importer.chartText
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.encore.EncoreClient
import com.rodrigomachado.riff.core.encore.HttpResponse
import com.rodrigomachado.riff.core.encore.HttpTransport
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class ChartDownloaderTest {
    private val tree = FakeFolderSource()
    private val repository = FakeChartRepository()
    private val writer = TreeWriter(tree)
    private val downloads = MemoryDownloads()
    private val matches = SavedMatches()
    private val access = object : FolderAccess {
        var writable = true

        override fun persistAccess(folderUri: String) = Unit

        override fun canWrite(folderUri: String) = writable
    }

    init {
        tree.file("raiz/outra/notes.chart", chartText("Outra", "Banda")) // a pasta existe
    }

    private fun validSng(title: String) = SngWriter.write(
        mapOf("name" to title, "artist" to "Nirvana"),
        mapOf("notes.chart" to chartText(title, "Nirvana").toByteArray()),
    )

    private fun encoreChart(md5: String) =
        EncoreChart(md5, "Teen", "Nirvana", "highfine", 300_000, setOf(Difficulty.EXPERT), hasVideoBackground = false)

    /** O "servidor" devolve [bytes]; com [hang], segura o download no meio até o teste cancelar. */
    private fun TestScope.downloader(bytes: ByteArray = ByteArray(0), hang: Boolean = false): ChartDownloader {
        val body: InputStream = if (hang) HangingStream(bytes) else bytes.inputStream()
        val transport = object : HttpTransport {
            override suspend fun post(url: String, json: String) = error("sem pesquisa")

            override suspend fun get(url: String, range: LongRange?) = HttpResponse(200, null, body)
        }
        val source = SngFolderSource(tree) { ByteArrayRandomRead(tree.bytes(it)) }
        return EncoreChartDownloader(
            EncoreClient(transport),
            object : LibraryFolderStore {
                override val rootUri = flowOf<String?>("raiz")

                override suspend fun setRootUri(uri: String) = Unit
            },
            access,
            writer,
            FolderChartImporter(source, repository, StandardTestDispatcher(testScheduler), 1L shl 24) { 1L },
            downloads,
            matches,
            Dispatchers.IO,
        ) { ByteArrayRandomRead(tree.bytes(it)) }
    }

    @Test
    fun aDownloadedPackageEntersTheLibraryAndIsRemembered() = runTest {
        val chart = downloader(validSng("Teen")).download(encoreChart("m5"), matchTrackUri = null) {}

        assertEquals("Teen", chart.title)
        assertEquals(listOf("raiz/Baixados pelo Riff/Nirvana - Teen.sng"), writer.created())
        assertEquals(setOf("m5"), downloads.md5s.first())
    }

    @Test
    fun aDownloadFromTheSpotifyScreenIsMatchedWithTheTrack() = runTest {
        val chart = downloader(validSng("Teen")).download(encoreChart("m5"), matchTrackUri = "spotify:track:x") {}

        assertEquals(Triple("spotify:track:x", chart.id, true), matches.saved.single())
    }

    @Test
    fun aBrokenDownloadLeavesNothingBehind() = runTest {
        val error = assertThrows(ChartDownloadException::class.java) {
            runBlocking { downloader("lixo".toByteArray()).download(encoreChart("m5"), null) {} }
        }

        assertEquals(ChartDownloadException.Reason.BROKEN_FILE, error.reason)
        assertEquals(emptyList<String>(), writer.created())
    }

    @Test
    fun leavingDuringTheDownloadDeletesThePartialFile() = runTest {
        val job = launch { downloader(validSng("Teen"), hang = true).download(encoreChart("m5"), null) {} }
        runCurrent()
        job.cancelAndJoin()

        assertEquals(emptyList<String>(), writer.created()) // revisão §4: nada de .part esquecido
    }

    @Test
    fun withoutWriteAccessNothingIsDownloaded() = runTest {
        access.writable = false

        val error = assertThrows(ChartDownloadException::class.java) {
            runBlocking { downloader(validSng("Teen")).download(encoreChart("m5"), null) {} }
        }

        assertEquals(ChartDownloadException.Reason.NO_WRITE_ACCESS, error.reason)
    }
}

/** Grava na árvore em memória; [created] lista os arquivos que ficaram na pasta de downloads. */
private class TreeWriter(private val tree: FakeFolderSource) : FolderWriter {
    override suspend fun deleteDocument(fileUri: String) = true

    private val paths = mutableSetOf<String>()

    fun created() = paths.toList()

    override suspend fun createFile(parentUri: String, subfolder: String, name: String): String =
        "$parentUri/$subfolder/$name".also {
            paths += it
            tree.file(it, ByteArray(0))
        }

    override fun openOutput(fileUri: String): OutputStream = object : ByteArrayOutputStream() {
        override fun close() = tree.file(fileUri, toByteArray())
    }

    override suspend fun nameOf(fileUri: String) = fileUri.substringAfterLast('/')

    override suspend fun rename(fileUri: String, newName: String): String {
        val target = fileUri.substringBeforeLast('/') + "/" + newName
        tree.file(target, tree.bytes(fileUri))
        tree.remove(fileUri)
        paths -= fileUri
        paths += target
        return target
    }

    override suspend fun delete(fileUri: String) {
        tree.remove(fileUri)
        paths -= fileUri
    }
}

/** Entrega metade e depois não chega mais nada, como uma conexão parada (o download sai pelo cancelamento). */
private class HangingStream(private val bytes: ByteArray) : InputStream() {
    private var sent = false

    override fun read(): Int = error("use read(buffer)")

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (!sent) {
            sent = true
            val n = minOf(len, bytes.size / 2)
            System.arraycopy(bytes, 0, b, off, n)
            return n
        }
        Thread.sleep(1)
        return 0 // nada chegou ainda: o download segue esperando até ser cancelado
    }
}

private class MemoryDownloads : EncoreDownloads {
    private val owned = MutableStateFlow(mapOf<String, String>())
    override val md5s = MutableStateFlow(emptySet<String>())

    override suspend fun remember(chartId: String, md5: String) {
        owned.value += md5 to chartId
        md5s.value = owned.value.keys
    }

    override suspend fun chartIdOf(md5: String) = owned.value[md5]
}

private class SavedMatches : TrackMatchRepository {
    val saved = mutableListOf<Triple<String, String, Boolean>>()
    override val changes = MutableStateFlow(0)

    override suspend fun matchFor(track: PlaybackState): MatchStatus = MatchStatus.NoMatch

    override suspend fun saveSuggestion(spotifyTrackUri: String, chartId: String, confidence: Float) = Unit

    override suspend fun save(spotifyTrackUri: String, chartId: String, confidence: Float, isManual: Boolean) {
        saved += Triple(spotifyTrackUri, chartId, isManual)
    }

    override suspend fun offsetFor(spotifyTrackUri: String, chartId: String): Int? = null

    override suspend fun saveOffset(spotifyTrackUri: String, chartId: String, offsetMs: Int) = Unit
}
