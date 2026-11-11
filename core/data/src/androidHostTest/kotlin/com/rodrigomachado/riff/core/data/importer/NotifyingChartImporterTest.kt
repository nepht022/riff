package com.rodrigomachado.riff.core.data.importer

import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NotifyingChartImporterTest {
    private val chart = Chart("id", "M", "B", null, null, null, "raiz/M", ChartFormat.CHART, setOf(Difficulty.EXPERT))

    private class Inner(private val fail: Boolean, private val chart: Chart) : ChartImporter {
        override suspend fun import(rootUri: String, onProgress: (Int) -> Unit): ImportReport {
            if (fail) throw java.io.IOException("pasta sumiu")
            return ImportReport(1, emptyList())
        }

        override suspend fun importPackage(fileUri: String, name: String): Chart = chart
    }

    /** Reimportar, trocar de pasta e baixar avisam ao fim: as capas guardadas podem ter mudado. */
    @Test
    fun eachImportNotifiesAtTheEnd() = runTest {
        val listeners = ChartImportListeners()
        var notified = 0
        listeners.add { notified++ }
        val importer = NotifyingChartImporter(Inner(fail = false, chart), listeners)

        importer.import("raiz")
        assertEquals(1, notified)
        assertEquals(chart, importer.importPackage("raiz/M.sng", "M.sng"))
        assertEquals(2, notified)
    }

    /** Mesmo uma importação que falhou no meio pode ter mudado parte da biblioteca. */
    @Test
    fun aFailedImportAlsoNotifies() = runTest {
        val listeners = ChartImportListeners()
        var notified = 0
        listeners.add { notified++ }

        runCatching { NotifyingChartImporter(Inner(fail = true, chart), listeners).import("raiz") }

        assertEquals(1, notified)
    }
}
