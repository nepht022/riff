package com.rodrigomachado.riff.core.data.game

import com.rodrigomachado.riff.core.data.importer.FakeChartRepository
import com.rodrigomachado.riff.core.data.importer.FakeFolderSource
import com.rodrigomachado.riff.core.data.importer.chartText
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderChartTrackLoaderTest {
    private val source = FakeFolderSource()
    private val charts = FakeChartRepository()

    private fun TestScope.loader() = FolderChartTrackLoader(source, charts, StandardTestDispatcher(testScheduler))

    private suspend fun registerChart(folder: String) = charts.saveScanned(
        Chart("id", "Música", "Banda", null, null, null, folder, ChartFormat.CHART, setOf(Difficulty.EXPERT)),
        scanId = 1,
    )

    @Test
    fun loadsTheNotesOfTheChosenDifficultyWithTheChartOffset() = runTest {
        source.file(
            "raiz/M/notes.chart",
            chartText("Música", "Banda").replace("[Song]\n{\n", "[Song]\n{\n  Offset = 0.1\n"),
        )
        source.file("raiz/M/song.ogg", "áudio")
        registerChart("raiz/M")

        val playable = loader().load("id", Difficulty.EXPERT)

        assertEquals(listOf(600L), playable.notes.map { it.timeMs })
        assertEquals("raiz/M/song.ogg", playable.audioUri)
    }

    @Test
    fun findsTheAudioIgnoringCaseAndPrefersTheFullSong() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        source.file("raiz/M/Guitar.ogg", "guitarra")
        source.file("raiz/M/SONG.MP3", "música")
        registerChart("raiz/M")

        assertEquals("raiz/M/SONG.MP3", loader().load("id", Difficulty.EXPERT).audioUri)
    }

    @Test
    fun folderWithoutAudioHasNoAudioUri() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        registerChart("raiz/M")

        assertNull(loader().load("id", Difficulty.EXPERT).audioUri)
    }

    @Test
    fun missingChartOrDifficultyIsChartNotFound() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        registerChart("raiz/M")

        assertTrue(
            runCatching { loader().load("outro", Difficulty.EXPERT) }.exceptionOrNull() is ChartNotFoundException,
        )
        assertTrue(runCatching { loader().load("id", Difficulty.EASY) }.exceptionOrNull() is ChartNotFoundException)
    }
}
