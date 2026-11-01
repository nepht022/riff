package com.rodrigomachado.riff.core.data.library

import com.rodrigomachado.riff.core.data.FakeAiSeparationRepository
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.folder.FolderWriter
import com.rodrigomachado.riff.core.data.importer.FakeChartRepository
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.OutputStream

class FolderChartDeleterTest {
    private val charts = FakeChartRepository()
    private val separations = FakeAiSeparationRepository()
    private val writer = DeletingWriter()
    private val folders = object : LibraryFolderStore {
        override val rootUri = MutableStateFlow<String?>("raiz")
        override suspend fun setRootUri(uri: String) = Unit
    }

    private class DeletingWriter : FolderWriter {
        val deleted = mutableListOf<String>()
        val refused = mutableSetOf<String>()
        override suspend fun deleteDocument(fileUri: String): Boolean {
            if (fileUri in refused) return false
            deleted += fileUri
            return true
        }
        override suspend fun createFile(parentUri: String, subfolder: String, name: String) = error("não usado")
        override fun openOutput(fileUri: String): OutputStream = error("não usado")
        override suspend fun nameOf(fileUri: String) = error("não usado")
        override suspend fun rename(fileUri: String, newName: String) = error("não usado")
        override suspend fun delete(fileUri: String) = error("não usado")
    }

    private fun TestScope.deleter() =
        FolderChartDeleter(charts, writer, folders, separations, StandardTestDispatcher(testScheduler))

    private suspend fun chart(id: String, folder: String) = charts.saveScanned(
        Chart(id, id, "Banda", null, null, null, folder, ChartFormat.CHART, setOf(Difficulty.EXPERT)),
        scanId = 1,
    )

    @Test
    fun aFolderChartLosesItsFolderAndLeavesTheLibrary() = runTest {
        chart("a", "raiz/A")
        chart("b", "raiz/B")

        val result = deleter().delete(listOf("a"))

        assertEquals(DeletionResult(deleted = 1, failed = 0), result)
        assertEquals(listOf("raiz/A"), writer.deleted)
        assertEquals(listOf("b"), charts.charts.map { it.id })
        assertEquals(1, separations.pruned) // a separação por IA dele vai junto
    }

    @Test
    fun aSngChartLosesItsFile() = runTest {
        chart("s", "raiz/Baixados pelo Riff/M.sng#sng")

        deleter().delete(listOf("s"))

        assertEquals(listOf("raiz/Baixados pelo Riff/M.sng"), writer.deleted)
    }

    @Test
    fun theLibraryRootIsNeverDeleted() = runTest {
        chart("r", "raiz")

        val result = deleter().delete(listOf("r"))

        assertEquals(DeletionResult(deleted = 0, failed = 1), result)
        assertTrue(writer.deleted.isEmpty())
        assertEquals(listOf("r"), charts.charts.map { it.id })
    }

    @Test
    fun chartsInsideTheDeletedFolderGoToo() = runTest {
        chart("a", "raiz/A")
        chart("dentro", "raiz/A/Bonus")
        chart("pacote", "raiz/A/x.sng#sng")
        chart("vizinho", "raiz/AB")

        assertEquals(2, deleter().alsoDeleted(listOf("a")))
        deleter().delete(listOf("a"))

        assertEquals(listOf("vizinho"), charts.charts.map { it.id })
    }

    @Test
    fun aFolderThatCannotBeDeletedStaysListed() = runTest {
        chart("a", "raiz/A")
        chart("b", "raiz/B")
        writer.refused += "raiz/A"

        val result = deleter().delete(listOf("a", "b"))

        assertEquals(DeletionResult(deleted = 1, failed = 1), result)
        assertEquals(listOf("a"), charts.charts.map { it.id })
    }

    @Test
    fun theRootIsRecognizedInSafUris() {
        val root = "content://ext/tree/primary%3ADownload%2FRiffCharts"
        assertTrue(LibraryFolders.isRoot("$root/document/primary%3ADownload%2FRiffCharts", root))
        assertFalse(LibraryFolders.isRoot("$root/document/primary%3ADownload%2FRiffCharts%2FSong", root))
        assertTrue(
            LibraryFolders.isInside(
                "$root/document/primary%3ADownload%2FRiffCharts%2FSong%2FBonus",
                "$root/document/primary%3ADownload%2FRiffCharts%2FSong",
            ),
        )
        assertFalse(
            LibraryFolders.isInside(
                "$root/document/primary%3ADownload%2FRiffCharts%2FSongs",
                "$root/document/primary%3ADownload%2FRiffCharts%2FSong",
            ),
        )
    }
}
