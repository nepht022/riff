package com.rodrigomachado.riff.core.data.game

import com.rodrigomachado.riff.core.data.importer.FakeChartRepository
import com.rodrigomachado.riff.core.data.importer.FakeFolderSource
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartAudioProbeTest {
    private val source = FakeFolderSource()
    private val charts = FakeChartRepository()

    @Test
    fun findsTheFolderAudioWithoutReadingTheNotes() = runTest {
        source.file("raiz/M/song.opus", "música")
        charts.saveScanned(
            Chart("id", "M", "B", null, null, null, "raiz/M", ChartFormat.CHART, setOf(Difficulty.EXPERT)),
            scanId = 1,
        )
        val probe = FolderChartAudioProbe(source, charts, StandardTestDispatcher(testScheduler))

        assertEquals(ChartAudio.Single("raiz/M/song.opus"), probe.audioOf("id"))
        assertNull(probe.audioOf("outro"))
    }
}
