package com.rodrigomachado.riff.core.data.download

import com.rodrigomachado.riff.core.chart.sng.SngRef
import com.rodrigomachado.riff.core.chart.sng.SngWriter
import com.rodrigomachado.riff.core.data.PreferencesLibraryFolderStore
import com.rodrigomachado.riff.core.data.RoomChartRepository
import com.rodrigomachado.riff.core.data.RoomEncoreDownloads
import com.rodrigomachado.riff.core.data.folder.FileFolderAccess
import com.rodrigomachado.riff.core.data.folder.FileFolderSource
import com.rodrigomachado.riff.core.data.folder.FileFolderWriter
import com.rodrigomachado.riff.core.data.folder.FileRandomReadOpener
import com.rodrigomachado.riff.core.data.folder.SngFolderSource
import com.rodrigomachado.riff.core.data.folder.pcUri
import com.rodrigomachado.riff.core.data.importer.FolderChartImporter
import com.rodrigomachado.riff.core.database.RiffDatabase
import com.rodrigomachado.riff.core.database.riffDatabase
import com.rodrigomachado.riff.core.datastore.RiffPreferences
import com.rodrigomachado.riff.core.datastore.riffDataStore
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.encore.EncoreClient
import com.rodrigomachado.riff.core.encore.HttpResponse
import com.rodrigomachado.riff.core.encore.HttpTransport
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Baixar no PC: dois mapas com o mesmo "Artista - Nome" não se atrapalham (como no celular, "Nome (2).sng"). */
class PcChartDownloaderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var root: File
    private lateinit var database: RiffDatabase
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Before
    fun setUp() {
        root = temp.newFolder("mapas")
        database = riffDatabase(File(temp.root, "riff.db"))
    }

    @After
    fun tearDown() {
        database.close()
        scope.cancel()
    }

    private val downloadsFolder get() = File(root, DOWNLOADS_FOLDER)

    /** Dois pacotes diferentes (notas diferentes) com o mesmo artista e nome. */
    private val twins = mapOf("m1" to sng(notes = "192 = N 0 0"), "m2" to sng(notes = "192 = N 1 0"))

    private val transport = object : HttpTransport {
        override suspend fun post(url: String, json: String) = error("sem pesquisa")

        override suspend fun get(url: String, range: LongRange?): HttpResponse {
            val bytes = twins.getValue(url.substringAfterLast('/').substringBefore('.'))
            return HttpResponse(200, bytes.size.toLong(), bytes.inputStream())
        }
    }

    private suspend fun downloader(): ChartDownloader {
        val folders = PreferencesLibraryFolderStore(
            RiffPreferences(riffDataStore(File(temp.root, "riff_settings.preferences_pb"), scope)),
        ).also { it.setRootUri(pcUri(root)) }
        val source = SngFolderSource(FileFolderSource(), FileRandomReadOpener())
        val importer = FolderChartImporter(
            source,
            RoomChartRepository(database.chartDao()),
            Dispatchers.IO,
            FolderChartImporter.MAX_FILE_BYTES,
        ) { System.currentTimeMillis() }
        return EncoreChartDownloader(
            EncoreClient(transport),
            folders,
            FileFolderAccess(),
            FileFolderWriter(),
            importer,
            RoomEncoreDownloads(database.encoreChartDao()),
            Dispatchers.IO,
            FileRandomReadOpener(),
        )
    }

    private fun encoreChart(md5: String) =
        EncoreChart(md5, "Teen", "Nirvana", "highfine", 300_000, setOf(Difficulty.EXPERT), hasVideoBackground = false)

    @Test
    fun twoChartsWithTheSameNameKeepTheirOwnFiles() = runBlocking {
        val downloader = downloader()

        val first = downloader.download(encoreChart("m1")) {}
        val second = downloader.download(encoreChart("m2")) {}

        val names = downloadsFolder.listFiles().orEmpty().map { it.name }.sorted()
        assertEquals(listOf("Nirvana - Teen (2).sng", "Nirvana - Teen.sng"), names)
        assertNotEquals(first.id, second.id)
        assertEquals(pcUri(File(downloadsFolder, "Nirvana - Teen.sng")), SngRef.packageFileOf(first.folderUri))
        assertEquals(pcUri(File(downloadsFolder, "Nirvana - Teen (2).sng")), SngRef.packageFileOf(second.folderUri))
        // Cada arquivo continua com o seu pacote (o segundo não sobrescreveu o primeiro).
        assertEquals(twins.getValue("m1").toList(), bytesOf("Nirvana - Teen.sng"))
        assertEquals(twins.getValue("m2").toList(), bytesOf("Nirvana - Teen (2).sng"))
    }

    /** Dois "Baixar" ao mesmo tempo: cada um grava no seu `.sng.part`. */
    @Test
    fun twoCreatesWithTheSameNameGetDifferentFiles() = runBlocking {
        val writer = FileFolderWriter()

        val first = writer.createFile(pcUri(root), DOWNLOADS_FOLDER, "Nirvana - Teen.sng.part")
        val second = writer.createFile(pcUri(root), DOWNLOADS_FOLDER, "Nirvana - Teen.sng.part")

        assertEquals("Nirvana - Teen.sng.part", File(first).name)
        assertEquals("Nirvana - Teen (2).sng.part", File(second).name)
    }

    private fun bytesOf(name: String) = File(downloadsFolder, name).readBytes().toList()

    private fun sng(notes: String) = SngWriter.write(
        mapOf("name" to "Teen", "artist" to "Nirvana"),
        mapOf(
            "notes.chart" to """
                [Song]
                {
                  Name = "Teen"
                  Artist = "Nirvana"
                }
                [ExpertSingle]
                {
                  $notes
                }
            """.trimIndent().toByteArray(),
        ),
    )
}
