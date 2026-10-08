package com.rodrigomachado.riff.feature.library

import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.data.importer.ChartImporter
import com.rodrigomachado.riff.core.data.importer.ImportReport
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

fun chart(
    id: String,
    title: String = "Música $id",
    artist: String = "Banda",
    difficulties: Set<Difficulty> = setOf(
        Difficulty.EXPERT,
    ),
) = Chart(id, title, artist, null, null, null, "content://$id", ChartFormat.CHART, difficulties)

class FakeChartRepository : ChartRepository {
    val charts = MutableStateFlow<List<Chart>>(emptyList())

    override fun observeCharts(): Flow<List<Chart>> = charts

    override suspend fun saveScanned(chart: Chart, scanId: Long) {
        charts.value = charts.value + chart
    }

    override suspend fun removeNotSeenIn(scanId: Long) = Unit

    override suspend fun keepFolder(folderUri: String, scanId: Long) = Unit
}

class FakeLibraryFolderStore(initial: String? = null) : LibraryFolderStore {
    override val rootUri = MutableStateFlow(initial)

    override suspend fun setRootUri(uri: String) {
        rootUri.value = uri
    }
}

class FakeFolderAccess : FolderAccess {
    val persisted = mutableListOf<String>()
    var failure: Exception? = null

    override fun persistReadAccess(folderUri: String) {
        failure?.let { throw it }
        persisted += folderUri
    }
}

/** Importador controlável: [nextResult] decide o que acontece; [gate] segura a importação no meio. */
class FakeChartImporter : ChartImporter {
    val calls = mutableListOf<String>()
    var nextResult: () -> ImportReport = { ImportReport(importedCount = 0, failures = emptyList()) }
    var progressToReport: Int? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun import(rootUri: String, onProgress: (Int) -> Unit): ImportReport {
        calls += rootUri
        progressToReport?.let(onProgress)
        gate?.await()
        return nextResult()
    }
}
