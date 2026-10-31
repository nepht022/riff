package com.rodrigomachado.riff.feature.library

import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.AiSeparationState
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.SeparatedFiles
import com.rodrigomachado.riff.core.data.SeparationScheduler
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.data.game.ChartAudio
import com.rodrigomachado.riff.core.data.game.ChartAudioProbe
import com.rodrigomachado.riff.core.data.importer.ChartImporter
import com.rodrigomachado.riff.core.data.importer.ImportReport
import com.rodrigomachado.riff.core.data.library.ChartDeleter
import com.rodrigomachado.riff.core.data.library.DeletionResult
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.QueuedSeparation
import com.rodrigomachado.riff.core.model.SeparationStatus
import com.rodrigomachado.riff.core.model.SeparationUsage
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

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

    override suspend fun getChart(id: String): Chart? = charts.value.firstOrNull { it.id == id }

    override suspend fun saveScanned(chart: Chart, scanId: Long) {
        charts.value = charts.value + chart
    }

    override suspend fun remove(ids: Collection<String>) {
        charts.value = charts.value.filterNot { it.id in ids }
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

    var writable = true

    override fun persistAccess(folderUri: String) {
        failure?.let { throw it }
        persisted += folderUri
    }

    override fun canWrite(folderUri: String) = writable
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

    override suspend fun importPackage(fileUri: String, name: String) = error("não usado na biblioteca")
}

class FakeAiSeparations : AiSeparationRepository {
    val states = mutableMapOf<String, AiSeparationState>()
    var pruned = 0
    override val separatedIds = MutableStateFlow(emptySet<String>())
    override val usage = MutableStateFlow(SeparationUsage(0, 0))
    override suspend fun stateOf(chartId: String) = states[chartId]
    override suspend fun separatedFiles(chartId: String): SeparatedFiles? = null
    override fun workDir(chartId: String) = File("x")
    override suspend fun commit(chartId: String, workDir: File) = Unit
    override suspend fun decline(chartId: String) {
        states[chartId] = AiSeparationState.DECLINED
    }
    override suspend fun resetDeclines() = Unit
    override suspend fun deleteAll() = Unit
    override suspend fun pruneOrphans() {
        pruned++
    }
}

class FakeScheduler : SeparationScheduler {
    override val status = MutableStateFlow<SeparationStatus>(SeparationStatus.Idle)
    override val queue = MutableStateFlow(emptyList<QueuedSeparation>())
    override val waitingForCharger = MutableStateFlow(false)
    override var lowMemoryDevice = false
    val enqueued = mutableListOf<String>()

    /** Uma música separando agora. */
    fun running(chartId: String) {
        status.value = SeparationStatus.Running(chartId, "Música $chartId", 0f, null)
    }

    override fun enqueue(songs: List<QueuedSeparation>) {
        enqueued += songs.map { it.chartId }
        queue.value += songs
    }

    var cancelled = false
    override fun cancelCurrent(chartId: String) {
        cancelled = true
    }
    override fun remove(chartId: String) = Unit
    override fun cancelAll() = Unit
    override fun dismissFailure() = Unit
}

class FakeAudioProbe : ChartAudioProbe {
    val audio = mutableMapOf<String, ChartAudio>()
    var failure: Exception? = null
    override suspend fun audioOf(chartId: String): ChartAudio? {
        failure?.let { throw it }
        return audio[chartId]
    }
}

class FakeSettingsRepository(initial: Settings = Settings.DEFAULT) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun update(transform: (Settings) -> Settings) {
        settings.value = transform(settings.value)
    }
}

class FakeChartDeleter(private val charts: FakeChartRepository) : ChartDeleter {
    val deleted = mutableListOf<List<String>>()
    var failing = emptySet<String>()
    var nested = 0
    override suspend fun alsoDeleted(chartIds: Collection<String>) = nested
    override suspend fun delete(chartIds: Collection<String>): DeletionResult {
        deleted += chartIds.toList()
        val ok = chartIds.filterNot { it in failing }
        charts.remove(ok)
        return DeletionResult(deleted = ok.size, failed = chartIds.size - ok.size)
    }
}
