package com.rodrigomachado.riff.core.data.importer

import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderChartImporterTest {
    private val source = FakeFolderSource()
    private val repository = FakeChartRepository()
    private var clock = 0L

    private fun TestScope.importer(maxFileBytes: Long = FolderChartImporter.MAX_FILE_BYTES) =
        FolderChartImporter(source, repository, StandardTestDispatcher(testScheduler), maxFileBytes) { ++clock }

    @Test
    fun importsEveryChartFolderInTheTreeAndReportsProgress() = runTest {
        source.file("raiz/Rock/Musica A/notes.chart", chartText("Música A", "Banda A"))
        source.file("raiz/Rock/Musica A/song.ini", "[song]\nname = Música A do ini")
        source.file("raiz/Musica B/notes.chart", chartText("Música B", "Banda B"))
        val progress = mutableListOf<Int>()

        val report = importer().import("raiz") { progress += it }

        assertEquals(ImportReport(importedCount = 2, failures = emptyList()), report)
        assertEquals(listOf("Música A do ini", "Música B"), repository.charts.map { it.title })
        assertEquals(setOf(Difficulty.EXPERT), repository.charts.first().difficulties)
        assertEquals("raiz/Rock/Musica A", repository.charts.first().folderUri)
        assertEquals(listOf(1, 2), progress)
    }

    @Test
    fun brokenChartIsReportedAndTheOthersStillImport() = runTest {
        source.file("raiz/Boa/notes.chart", chartText("Boa", "Banda"))
        source.file("raiz/Quebrada/notes.chart", chartText("Quebrada", "Banda", notes = "  abc = N 0 0"))

        val report = importer().import("raiz")

        assertEquals(1, report.importedCount)
        assertEquals("Quebrada", report.failures.single().folderName)
        assertEquals(ImportFailureKind.INVALID_CHART, report.failures.single().kind)
        assertTrue(report.failures.single().detail.orEmpty().contains("linha"))
        assertEquals(listOf("Boa"), repository.charts.map { it.title })
    }

    @Test
    fun reimportDoesNotDuplicateAndDropsFoldersThatDisappeared() = runTest {
        source.file("raiz/A/notes.chart", chartText("A", "Banda"))
        source.file("raiz/B/notes.chart", chartText("B", "Banda"))
        importer().import("raiz")

        source.remove("raiz/B")
        val report = importer().import("raiz")

        assertEquals(1, report.importedCount)
        assertEquals(listOf("A"), repository.charts.map { it.title })
    }

    @Test
    fun lostAccessToTheRootThrowsAndKeepsTheLibrary() = runTest {
        source.file("raiz/A/notes.chart", chartText("A", "Banda"))
        importer().import("raiz")
        source.revoked += "raiz"

        val error = runCatching { importer().import("raiz") }.exceptionOrNull()

        assertTrue(error is FolderAccessLostException)
        assertEquals(listOf("A"), repository.charts.map { it.title })
        assertEquals(1, repository.removedScans.size) // só a primeira importação limpou
    }

    @Test
    fun oversizedNotesFileIsRejectedWithoutBeingRead() = runTest {
        source.file("raiz/Gigante/notes.chart", ByteArray(4), declaredSize = 200L * 1024 * 1024)

        val report = importer().import("raiz")

        assertEquals(0, report.importedCount)
        assertEquals(ImportFailureKind.FILE_TOO_LARGE, report.failures.single().kind)
        assertFalse(source.reads.any { it.contains("Gigante") })
    }

    @Test
    fun stopsDescendingPastTheMaximumDepth() = runTest {
        val deep = (1..10).joinToString("/") { "n$it" }
        source.file("raiz/$deep/notes.chart", chartText("Funda", "Banda"))
        source.file("raiz/Rasa/notes.chart", chartText("Rasa", "Banda"))

        val report = importer().import("raiz")

        assertEquals(listOf("Rasa"), repository.charts.map { it.title })
        assertEquals(1, report.importedCount)
    }

    @Test
    fun missingTitleUsesFolderNameAndMissingArtistIsBlank() = runTest {
        source.file("raiz/Pasta Sem Nome/notes.chart", chartText(title = null, artist = null))

        importer().import("raiz")

        val chart = repository.charts.single()
        assertEquals("Pasta Sem Nome", chart.title)
        assertEquals("", chart.artist)
    }

    @Test
    fun rootThatIsItselfASongFolderIsImported() = runTest {
        source.file("raiz/notes.chart", chartText("Direto", "Banda"))

        assertEquals(1, importer().import("raiz").importedCount)
    }

    @Test
    fun unexpectedErrorReadingAFileBecomesAFailureAndTheOthersStillImport() = runTest {
        source.file("raiz/A/notes.chart", chartText("A", "Banda"))
        source.file("raiz/B/notes.chart", chartText("B", "Banda"))
        source.readFailures["raiz/A/notes.chart"] = SecurityException("permissão revogada no meio")

        val report = importer().import("raiz")

        assertEquals(listOf("B"), repository.charts.map { it.title })
        assertEquals("A", report.failures.single().folderName)
    }

    @Test
    fun subfolderThatCannotBeListedKeepsItsChartsInsteadOfDeletingThem() = runTest {
        source.file("raiz/A/notes.chart", chartText("A", "Banda"))
        source.file("raiz/B/notes.chart", chartText("B", "Banda"))
        importer().import("raiz")
        source.revoked += "raiz/B"

        val report = importer().import("raiz")

        assertEquals("B", report.failures.single().folderName)
        assertEquals(listOf("A", "B"), repository.charts.map { it.title })
    }

    @Test
    fun fileWithUnknownSizeIsStillCappedWhileReading() = runTest {
        source.file("raiz/Sem Tamanho/notes.chart", ByteArray(200), declaredSize = 0)

        val report = importer(maxFileBytes = 100).import("raiz")

        assertEquals(0, report.importedCount)
        assertEquals(ImportFailureKind.FILE_TOO_LARGE, report.failures.single().kind)
    }

    @Test
    fun previouslyImportedChartSurvivesATemporaryReadFailure() = runTest {
        source.file("raiz/A/notes.chart", chartText("A", "Banda"))
        source.file("raiz/B/notes.chart", chartText("B", "Banda"))
        importer().import("raiz")
        source.readFailures["raiz/A/notes.chart"] = java.io.IOException("cartão SD desconectou")

        importer().import("raiz")

        assertEquals(listOf("A", "B"), repository.charts.map { it.title })
    }

    @Test
    fun theSameChartInTwoFoldersCountsOnceAndTheCopyIsReported() = runTest {
        source.file("raiz/Original/notes.chart", chartText("Música", "Banda"))
        source.file("raiz/Copia/notes.chart", chartText("Música", "Banda"))

        val report = importer().import("raiz")

        assertEquals(1, report.importedCount)
        assertEquals(1, repository.charts.size)
        val duplicate = report.failures.single()
        assertEquals(ImportFailureKind.DUPLICATE, duplicate.kind)
        assertEquals(setOf("Original", "Copia"), setOf(duplicate.folderName, duplicate.detail))
    }
}
