package com.rodrigomachado.riff.core.data.folder

import com.rodrigomachado.riff.core.chart.sng.SngWriter
import com.rodrigomachado.riff.core.data.NoAiSeparations
import com.rodrigomachado.riff.core.data.PreferencesLibraryFolderStore
import com.rodrigomachado.riff.core.data.RoomChartRepository
import com.rodrigomachado.riff.core.data.importer.FolderChartImporter
import com.rodrigomachado.riff.core.data.library.FolderChartDeleter
import com.rodrigomachado.riff.core.database.RiffDatabase
import com.rodrigomachado.riff.core.database.riffDatabase
import com.rodrigomachado.riff.core.datastore.RiffPreferences
import com.rodrigomachado.riff.core.datastore.riffDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** A biblioteca numa pasta do PC: listar, importar, pasta que some e excluir (a raiz nunca). */
class PcLibraryTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var root: File
    private lateinit var database: RiffDatabase
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val source = SngFolderSource(FileFolderSource(), FileRandomReadOpener())

    @Before
    fun setUp() {
        root = temp.newFolder("mapas")
        File(root, "Música Á").apply { mkdirs() }.run {
            File(this, "notes.chart").writeText(chartText("Canção", "Banda"))
            File(this, "song.ogg").writeBytes(ByteArray(10))
        }
        File(root, "Pacote B.sng").writeBytes(
            SngWriter.write(
                mapOf("name" to "Pacote", "artist" to "Outra"),
                mapOf("notes.chart" to chartText("Pacote", "Outra").toByteArray(), "song.opus" to ByteArray(10)),
            ),
        )
        File(root, "vazia").mkdirs()
        File(root, "quebrada").apply { mkdirs() }.run { File(this, "notes.chart").writeText("isto não é um mapa") }
        database = riffDatabase(File(temp.root, "riff.db"))
    }

    @After
    fun tearDown() {
        database.close()
        scope.cancel()
    }

    private val rootUri get() = pcUri(root)
    private val charts by lazy { RoomChartRepository(database.chartDao()) }
    private fun importer() = FolderChartImporter(source, charts, Dispatchers.IO, FolderChartImporter.MAX_FILE_BYTES) {
        System.currentTimeMillis()
    }

    @Test
    fun listsFoldersAndSngWithAccentsAndSpaces() = runBlocking {
        val names = FileFolderSource().listChildren(rootUri).map { it.name }.toSet()

        assertEquals(setOf("Música Á", "Pacote B.sng", "vazia", "quebrada"), names)
    }

    @Test
    fun importingCountsTheBrokenOnesAndGoesOn() = runBlocking {
        val report = importer().import(rootUri)

        assertEquals(2, report.importedCount)
        assertEquals(listOf("quebrada"), report.failures.map { it.folderName })
        assertEquals(setOf("Canção", "Pacote"), charts.observeCharts().first().map { it.title }.toSet())
    }

    @Test
    fun aFolderThatDisappearedRaisesAccessLost() {
        val uri = rootUri
        root.deleteRecursively()

        assertThrows(FolderAccessLostException::class.java) { runBlocking { FileFolderSource().listChildren(uri) } }
    }

    @Test
    fun deletingAChartRemovesOnlyItsFolder() = runBlocking {
        importer().import(rootUri)
        val folders = PreferencesLibraryFolderStore(
            RiffPreferences(riffDataStore(File(temp.root, "riff_settings.preferences_pb"), scope)),
        )
        folders.setRootUri(rootUri)
        val song = charts.observeCharts().first().single { it.title == "Canção" }

        val result = FolderChartDeleter(charts, FileFolderWriter(), folders, NoAiSeparations, Dispatchers.IO)
            .delete(listOf(song.id))

        assertEquals(1, result.deleted)
        assertFalse(File(root, "Música Á").exists())
        assertTrue(root.exists() && File(root, "Pacote B.sng").exists())
    }

    @Test
    fun theLibraryRootIsNeverDeleted() = runBlocking {
        File(root, "notes.chart").writeText(chartText("Solto", "Na Raiz"))
        importer().import(rootUri)
        val folders = PreferencesLibraryFolderStore(
            RiffPreferences(riffDataStore(File(temp.root, "riff_settings.preferences_pb"), scope)),
        )
        folders.setRootUri(rootUri)
        val loose = charts.observeCharts().first().single { it.title == "Solto" }

        val result = FolderChartDeleter(charts, FileFolderWriter(), folders, NoAiSeparations, Dispatchers.IO)
            .delete(listOf(loose.id))

        assertEquals(1, result.failed)
        assertTrue(File(root, "Música Á").exists())
    }

    private fun chartText(title: String, artist: String) = """
        [Song]
        {
          Name = "$title"
          Artist = "$artist"
        }
        [ExpertSingle]
        {
          192 = N 0 0
        }
    """.trimIndent()
}
