package com.rodrigomachado.riff.core.data.game

import com.rodrigomachado.riff.core.chart.sng.ByteArrayRandomRead
import com.rodrigomachado.riff.core.chart.sng.SngRef
import com.rodrigomachado.riff.core.chart.sng.SngWriter
import com.rodrigomachado.riff.core.data.FakeAiSeparationRepository
import com.rodrigomachado.riff.core.data.SeparatedFiles
import com.rodrigomachado.riff.core.data.folder.SngFolderSource
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
import java.io.File

class FolderChartTrackLoaderTest {
    private val source = FakeFolderSource()
    private val charts = FakeChartRepository()
    private val separations = FakeAiSeparationRepository()

    private fun TestScope.loader() = FolderChartTrackLoader(
        source,
        charts,
        separations,
        StandardTestDispatcher(testScheduler),
    )

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
        assertEquals(ChartAudio.Single("raiz/M/song.ogg"), playable.audio)
    }

    @Test
    fun guitarWithTheRestOfTheBandBecomesStemsIgnoringCase() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        source.file("raiz/M/Guitar.ogg", "guitarra")
        source.file("raiz/M/SONG.MP3", "música")
        source.file("raiz/M/drums_1.ogg", "bateria")
        source.file("raiz/M/bass.opus", "baixo")
        registerChart("raiz/M")

        assertEquals(
            ChartAudio.Stems(
                guitarUri = "raiz/M/Guitar.ogg",
                backingUris = listOf("raiz/M/SONG.MP3", "raiz/M/bass.opus", "raiz/M/drums_1.ogg"),
                fallbackUri = "raiz/M/SONG.MP3",
            ),
            loader().load("id", Difficulty.EXPERT).audio,
        )
    }

    @Test
    fun stemsWithoutSongFallBackToTheGuitar() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        source.file("raiz/M/guitar.ogg", "guitarra")
        source.file("raiz/M/rhythm.ogg", "base")
        registerChart("raiz/M")

        assertEquals("raiz/M/guitar.ogg", loader().load("id", Difficulty.EXPERT).audio?.singleUri)
    }

    @Test
    fun onlyTheSongIsASingleFile() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        source.file("raiz/M/song.wav", "música")
        registerChart("raiz/M")

        assertEquals(ChartAudio.Single("raiz/M/song.wav"), loader().load("id", Difficulty.EXPERT).audio)
    }

    @Test
    fun theGuitarAloneIsASingleFile() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        source.file("raiz/M/guitar.mp3", "guitarra")
        registerChart("raiz/M")

        assertEquals(ChartAudio.Single("raiz/M/guitar.mp3"), loader().load("id", Difficulty.EXPERT).audio)
    }

    @Test
    fun folderWithoutAudioHasNoAudio() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        registerChart("raiz/M")

        assertNull(loader().load("id", Difficulty.EXPERT).audio)
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

    @Test
    fun theAudioOfASngPointsInsideThePackage() = runTest {
        source.file(
            "raiz/x.sng",
            SngWriter.write(
                mapOf("name" to "Música"),
                mapOf(
                    "notes.chart" to chartText("Música", "Banda").toByteArray(),
                    "guitar.ogg" to ByteArray(4),
                    "song.opus" to ByteArray(4),
                ),
            ),
        )
        registerChart(SngRef.packageUri("raiz/x.sng"))
        val loader = FolderChartTrackLoader(
            SngFolderSource(source) { ByteArrayRandomRead(source.bytes(it)) },
            charts,
            separations,
            StandardTestDispatcher(testScheduler),
        )

        val playable = loader.load("id", Difficulty.EXPERT)

        val song = SngRef.entryUri("raiz/x.sng", "song.opus")
        assertEquals(ChartAudio.Stems(SngRef.entryUri("raiz/x.sng", "guitar.ogg"), listOf(song), song), playable.audio)
    }

    @Test
    fun anAiSeparationBecomesStemsWithTheSongAsFallback() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        source.file("raiz/M/song.opus", "música")
        registerChart("raiz/M")
        separations.files["id"] = SeparatedFiles(File("/sep/x/guitar.m4a"), File("/sep/x/backing.m4a"))

        assertEquals(
            ChartAudio.Stems("file:///sep/x/guitar.m4a", listOf("file:///sep/x/backing.m4a"), "raiz/M/song.opus"),
            loader().load("id", Difficulty.EXPERT).audio,
        )
    }

    @Test
    fun theChartsOwnStemsWinOverTheAiOnes() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        source.file("raiz/M/guitar.ogg", "guitarra")
        source.file("raiz/M/song.ogg", "música")
        registerChart("raiz/M")
        separations.files["id"] = SeparatedFiles(File("/sep/x/guitar.m4a"), File("/sep/x/backing.m4a"))

        assertEquals("raiz/M/guitar.ogg", (loader().load("id", Difficulty.EXPERT).audio as ChartAudio.Stems).guitarUri)
    }
}
