package com.rodrigomachado.riff.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.AiSeparationState
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.ScoreRepository
import com.rodrigomachado.riff.core.data.SeparationScheduler
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.game.ChartAudio
import com.rodrigomachado.riff.core.data.game.ChartAudioProbe
import com.rodrigomachado.riff.core.data.importer.ChartImporter
import com.rodrigomachado.riff.core.data.importer.ImportReport
import com.rodrigomachado.riff.core.data.library.ChartDeleter
import com.rodrigomachado.riff.core.data.library.LibraryFolders
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.QueuedSeparation
import com.rodrigomachado.riff.core.model.SeparationStatus
import com.rodrigomachado.riff.core.ui.art.ChartArtImages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val isLoading: Boolean = true,
    val hasFolder: Boolean = false,
    val charts: List<Chart> = emptyList(),
    val isImporting: Boolean = false,
    val scannedFolders: Int = 0,
    val report: ImportReport? = null,
    val folderAccessLost: Boolean = false,
    val importFailed: Boolean = false,
    /** Músicas com a guitarra separada pela IA (a marca na lista). */
    val aiSeparated: Set<String> = emptySet(),
    /** "Separar a guitarra com IA?" aberta. */
    val prompt: SeparationAsk? = null,
    /** Pronto para jogar (a tela navega e avisa com onPlayHandled). */
    val play: PlayRequestLibrary? = null,
    /** Excluir mapas: o modo de seleção, a confirmação e os avisos. */
    val deletion: DeletionState = DeletionState(),
    /** Mapas soltos na raiz da biblioteca: apagar a pasta deles apagaria a biblioteca inteira. */
    val protectedIds: Set<String> = emptySet(),
    /** Das selecionadas, quantas dá para pôr na fila de separação ("Separar (n)"). */
    val separableSelected: Int = 0,
    /** O maior recorde de cada mapa jogado, em qualquer dificuldade ("Recorde N · Dificuldade" no cartão). */
    val bests: Map<String, ChartBest> = emptyMap(),
)

/** O recorde de um mapa: os pontos e a dificuldade em que foi feito. */
data class ChartBest(val score: Long, val difficulty: Difficulty)

data class DeletionState(
    val selecting: Boolean = false,
    val selected: Set<String> = emptySet(),
    val ask: DeleteAsk? = null,
    /** Quantos mapas não deu para excluir (0 = nenhum aviso). */
    val failed: Int = 0,
    val needsPermission: Boolean = false,
)

/** "Excluir?" — [title] quando é um mapa só; [alsoDeleted] mapas dentro das pastas vão junto. */
data class DeleteAsk(val ids: List<String>, val title: String?, val alsoDeleted: Int)

data class SeparationAsk(
    val chartId: String,
    val title: String,
    val difficulty: Difficulty,
    val lowMemory: Boolean,
    /** Já há separação: "Separar" vira "+ Fila". */
    val busy: Boolean = false,
    /** Ocupado só pela fila esperando (o carregador, por exemplo), sem nenhuma separando agora. */
    val queueWaiting: Boolean = false,
)

data class PlayRequestLibrary(val chartId: String, val difficulty: Difficulty)

private data class PromptState(val prompt: SeparationAsk? = null, val play: PlayRequestLibrary? = null)

private data class Extras(
    val prompt: PromptState,
    val separated: Set<String>,
    val deletion: DeletionState,
    val separationBusy: Set<String>,
    val bests: Map<String, ChartBest>,
)

/** As selecionadas que precisam da IA, na ordem da lista: sem guitarra do mapa, sem separação, fora da fila. */
private fun separable(charts: List<Chart>, selected: Set<String>, separated: Set<String>, busy: Set<String>) =
    charts.filter { it.id in selected && !it.separateGuitar && it.id !in separated && it.id !in busy }

private data class ImportState(
    val isImporting: Boolean = false,
    val scannedFolders: Int = 0,
    val report: ImportReport? = null,
    val folderAccessLost: Boolean = false,
    val importFailed: Boolean = false,
)

/**
 * A Biblioteca: os mapas, a importação da pasta, a exclusão, a pergunta da separação e o recorde de cada mapa
 * ([scores]). [images]: as capas das listas (as pequenas, guardadas na memória).
 */
@Suppress("TooManyFunctions", "LongParameterList") // importação, exclusão e separação dividem o mesmo estado
open class LibraryViewModel(
    chartRepository: ChartRepository,
    private val folderStore: LibraryFolderStore,
    private val importer: ChartImporter,
    private val folderAccess: FolderAccess,
    private val separations: AiSeparationRepository,
    private val scheduler: SeparationScheduler,
    private val probe: ChartAudioProbe,
    private val settings: SettingsRepository,
    private val deleter: ChartDeleter,
    scores: ScoreRepository,
    val images: ChartArtImages?,
) : ViewModel() {
    private val importState = MutableStateFlow(ImportState())
    private val promptState = MutableStateFlow(PromptState())
    private val deletionState = MutableStateFlow(DeletionState())

    /** A que está separando e as da fila: não são perguntadas nem entram de novo. */
    private val separationBusyIds = combine(scheduler.status, scheduler.queue) { status, queue ->
        queue.map { it.chartId }.toSet() + listOfNotNull((status as? SeparationStatus.Running)?.chartId)
    }
    private val bests = scores.observeChartBests().map { records ->
        records.associate { it.chartId to ChartBest(it.score, it.difficulty) }
    }
    private val extras =
        combine(promptState, separations.separatedIds, deletionState, separationBusyIds, bests, ::Extras)

    val uiState: StateFlow<LibraryUiState> =
        combine(
            chartRepository.observeCharts(),
            folderStore.rootUri,
            importState,
            extras,
        ) { charts, rootUri, import, extras ->
            val (prompt, separated, deletion) = extras
            LibraryUiState(
                isLoading = false,
                hasFolder = rootUri != null,
                charts = charts,
                isImporting = import.isImporting,
                scannedFolders = import.scannedFolders,
                report = import.report,
                folderAccessLost = import.folderAccessLost,
                importFailed = import.importFailed,
                aiSeparated = separated,
                prompt = prompt.prompt,
                play = prompt.play,
                deletion = deletion,
                protectedIds = charts.filter { LibraryFolders.isRoot(it.folderUri, rootUri) }.map { it.id }.toSet(),
                separableSelected = separable(charts, deletion.selected, separated, extras.separationBusy).size,
                bests = extras.bests,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    /** Depois da dificuldade: pergunta se a música não tem guitarra separada (nem da IA, nem recusa guardada). */
    fun onDifficultyChosen(chart: Chart, difficulty: Difficulty) {
        viewModelScope.launch {
            promptState.value = if (needsAsking(chart.id)) {
                val separating = scheduler.status.value is SeparationStatus.Running
                val queued = scheduler.queue.value.isNotEmpty()
                PromptState(
                    prompt = SeparationAsk(
                        chart.id,
                        chart.title,
                        difficulty,
                        scheduler.lowMemoryDevice,
                        busy = separating || queued,
                        queueWaiting = queued && !separating,
                    ),
                )
            } else {
                PromptState(play = PlayRequestLibrary(chart.id, difficulty))
            }
        }
    }

    private suspend fun needsAsking(chartId: String): Boolean {
        val running = scheduler.status.value
        val separating = running is SeparationStatus.Running && running.chartId == chartId
        if (separating || scheduler.queue.value.any { it.chartId == chartId }) return false
        when (separations.stateOf(chartId)) {
            AiSeparationState.SEPARATED -> return false
            AiSeparationState.DECLINED -> if (!settings.settings.first().askSeparationAlways) return false
            null -> Unit
        }
        // Sem conseguir ler a pasta (apagada, sem permissão), não pergunta: a partida mostra o erro de sempre.
        val audio = try {
            probe.audioOf(chartId)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            null
        }
        return audio != null && audio !is ChartAudio.Stems
    }

    /** "Separar" ou, com outra separando, "+ Fila": as duas põem a música na fila. */
    fun onSeparate() {
        val ask = promptState.value.prompt ?: return
        scheduler.enqueue(listOf(QueuedSeparation(ask.chartId, ask.title)))
        promptState.value = PromptState()
    }

    /** "Separar (n)" na seleção: põe na fila as que precisam, na ordem da lista, e sai da seleção. */
    fun onSeparateSelected() {
        val state = uiState.value
        val busy = scheduler.queue.value.map { it.chartId }.toSet() +
            listOfNotNull((scheduler.status.value as? SeparationStatus.Running)?.chartId)
        val songs = separable(state.charts, deletionState.value.selected, state.aiSeparated, busy)
        if (songs.isNotEmpty()) scheduler.enqueue(songs.map { QueuedSeparation(it.id, it.title) })
        deletionState.value = DeletionState()
    }

    fun onPlayWithoutSeparating() {
        val ask = promptState.value.prompt ?: return
        viewModelScope.launch {
            // Ocupado com outra separação, "jogar sem separar" é só por agora: a pergunta volta da próxima vez.
            if (!ask.busy) separations.decline(ask.chartId)
            promptState.value = PromptState(play = PlayRequestLibrary(ask.chartId, ask.difficulty))
        }
    }

    fun onStartSelecting() {
        deletionState.value = DeletionState(selecting = true)
    }

    fun onToggleSelected(chartId: String) = deletionState.update {
        it.copy(selected = if (chartId in it.selected) it.selected - chartId else it.selected + chartId)
    }

    /** Marca todos os mapas que dá para excluir; com todos já marcados, desmarca. */
    fun onToggleAll() = deletionState.update { deletion ->
        val deletable = uiState.value.let { state -> state.charts.map { it.id }.filter { it !in state.protectedIds } }
        deletion.copy(selected = if (deletion.selected.containsAll(deletable)) emptySet() else deletable.toSet())
    }

    fun onCancelSelecting() {
        deletionState.value = DeletionState()
    }

    fun onDeleteSelected() = onDeleteRequested(
        uiState.value.charts.map { it.id }.filter { it in deletionState.value.selected },
    )

    /** O X de um mapa ou o "Excluir (n)": confere a permissão de gravar e pede confirmação. */
    fun onDeleteRequested(chartIds: List<String>) {
        if (chartIds.isEmpty()) return
        viewModelScope.launch {
            val root = folderStore.rootUri.first()
            if (root == null || !folderAccess.canWrite(root)) {
                deletionState.update { it.copy(needsPermission = true) }
                return@launch
            }
            val title = chartIds.singleOrNull()?.let { id -> uiState.value.charts.firstOrNull { it.id == id }?.title }
            deletionState.update { it.copy(ask = DeleteAsk(chartIds, title, deleter.alsoDeleted(chartIds))) }
        }
    }

    fun onDeleteDismissed() = deletionState.update { it.copy(ask = null, needsPermission = false) }

    fun onDeleteConfirmed() {
        val ask = deletionState.value.ask ?: return
        deletionState.update { it.copy(ask = null) }
        viewModelScope.launch {
            val running = scheduler.status.value
            if (running is SeparationStatus.Running && running.chartId in ask.ids) {
                scheduler.cancelCurrent(running.chartId)
            }
            val result = deleter.delete(ask.ids)
            deletionState.value = DeletionState(failed = result.failed)
        }
    }

    fun onDeletionFailureShown() = deletionState.update { it.copy(failed = 0) }

    fun onPromptDismissed() {
        promptState.value = PromptState()
    }

    fun onPlayHandled() {
        promptState.update { it.copy(play = null) }
    }

    @Suppress("SwallowedException") // a exceção vira o estado de erro da tela
    fun onFolderPicked(uri: String) {
        try {
            folderAccess.persistAccess(uri)
        } catch (e: SecurityException) {
            importState.value = ImportState(folderAccessLost = true)
            return
        }
        viewModelScope.launch {
            folderStore.setRootUri(uri)
            runImport(uri)
        }
    }

    /** Desistiu de escolher a pasta: sai do erro de acesso e volta à lista (os mapas continuam listados). */
    fun onFolderPickCancelled() {
        importState.update { if (it.folderAccessLost) ImportState() else it }
    }

    fun onRescan() {
        viewModelScope.launch { folderStore.rootUri.first()?.let { runImport(it) } }
    }

    fun onReportDismissed() {
        importState.update { it.copy(report = null) }
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught") // qualquer erro vira o estado de erro da tela
    private suspend fun runImport(uri: String) {
        if (importState.value.isImporting) return
        importState.value = ImportState(isImporting = true)
        importState.value = try {
            val report = importer.import(uri) { scanned -> importState.update { it.copy(scannedFolders = scanned) } }
            separations.pruneOrphans() // as separações de músicas que saíram da biblioteca
            ImportState(report = report)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FolderAccessLostException) {
            ImportState(folderAccessLost = true)
        } catch (e: Exception) {
            ImportState(importFailed = true)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
