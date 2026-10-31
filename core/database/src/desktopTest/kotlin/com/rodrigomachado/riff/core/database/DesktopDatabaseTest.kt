package com.rodrigomachado.riff.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DesktopDatabaseTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun aChartSavedOnThePcIsReadBackAfterReopening() = runTest {
        val file = File(folder.root, "riff.db")
        riffDatabase(file).run {
            chartDao().upsert(ChartEntity("a", "Música", "Banda", null, null, null, "C:\\mapas\\a", "CHART", 1))
            close()
        }

        val again = riffDatabase(file)
        val ids = again.chartDao().observeAll().first().map { it.chart.id }
        again.close()

        assertEquals(listOf("a"), ids)
    }
}
