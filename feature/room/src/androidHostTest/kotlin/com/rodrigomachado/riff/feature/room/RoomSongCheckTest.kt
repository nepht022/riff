package com.rodrigomachado.riff.feature.room

import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.EncoreDownloads
import com.rodrigomachado.riff.core.data.download.ChartDownloader
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.room.EncoreRef
import com.rodrigomachado.riff.core.room.RoomSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A biblioteca de verdade vista pela sala: o mapa, o md5 do Encore e o download conferido pelo `chartId`. */
class RoomSongCheckTest {
    private val charts = MemoryCharts()
    private val downloads = object : EncoreDownloads {
        val md5s0 = mutableMapOf(CHART_A to MD5_A)
        override val md5s: Flow<Set<String>> = flowOf(md5s0.values.toSet())

        override suspend fun remember(chartId: String, md5: String) {
            md5s0[chartId] = md5
        }

        override suspend fun chartIdOf(md5: String) = md5s0.entries.firstOrNull { it.value == md5 }?.key

        override suspend fun md5Of(chartId: String) = md5s0[chartId]
    }
    private val asked = mutableListOf<EncoreChart>()
    private val downloader = object : ChartDownloader {
        override suspend fun download(chart: EncoreChart, onProgress: (Float) -> Unit): Chart {
            asked += chart
            return chart(CHART_B).also { charts.items[it.id] = it }
        }
    }
    private val check = RoomSongCheck.of(charts, downloads, downloader)

    @Test
    fun aLibrarySongGoesToTheRoomWithItsEncorePackage() = runTest {
        charts.items[CHART_A] = chart(CHART_A, title = "T".repeat(300))

        val song = check.songOf(CHART_A)

        assertEquals(200, song?.title?.length)
        assertEquals("Banda", song?.artist)
        assertEquals(setOf(Difficulty.HARD, Difficulty.EXPERT), song?.difficulties)
        assertEquals(EncoreRef(MD5_A, video = false, charter = "eu"), song?.encore)
        assertTrue(check.has(CHART_A))
        assertFalse(check.has(CHART_B))
        assertNull(check.songOf(CHART_B))
    }

    @Test
    fun aLocalSongHasNoEncore() = runTest {
        charts.items[CHART_B] = chart(CHART_B)

        assertNull(check.songOf(CHART_B)?.encore)
    }

    /** O download confere pelo `chartId` da sala: o pacote baixado pode ter dado outro mapa. */
    @Test
    fun downloadChecksTheChartIdAgain() = runTest {
        val wanted = RoomSong(CHART_B, "M", "B", setOf(Difficulty.EXPERT), 1_000, EncoreRef(MD5_A, true, null))

        assertTrue(check.download(wanted) {})
        assertEquals(MD5_A, asked.single().md5)
        assertTrue(asked.single().hasVideoBackground)
        assertFalse(check.download(wanted.copy(chartId = CHART_A)) {})
        assertFalse(check.download(wanted.copy(encore = null)) {})
        assertFalse(check.canDownload(wanted.copy(encore = null)))
        assertTrue(check.canDownload(wanted))
        assertFalse(RoomSongCheck.NONE.canDownload(wanted))
        assertFalse(RoomSongCheck.NONE.download(wanted) {})
    }

    private fun chart(id: String, title: String = "Música") = Chart(
        id, title, "Banda", null, "eu", 1_000, "u", ChartFormat.CHART, setOf(Difficulty.HARD, Difficulty.EXPERT),
    )

    private class MemoryCharts : ChartRepository {
        val items = mutableMapOf<String, Chart>()

        override fun observeCharts(): Flow<List<Chart>> = flowOf(items.values.toList())

        override suspend fun getChart(id: String): Chart? = items[id]

        override suspend fun saveScanned(chart: Chart, scanId: Long) = Unit

        override suspend fun remove(ids: Collection<String>) = Unit

        override suspend fun removeNotSeenIn(scanId: Long) = Unit

        override suspend fun keepFolder(folderUri: String, scanId: Long) = Unit
    }
}
