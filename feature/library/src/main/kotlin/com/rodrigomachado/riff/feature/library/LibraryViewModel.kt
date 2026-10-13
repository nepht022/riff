package com.rodrigomachado.riff.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.AiSeparationState
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.SeparationScheduler
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.TrackMatchRepository
import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.game.ChartAudio
import com.rodrigomachado.riff.core.data.game.ChartAudioProbe
import com.rodrigomachado.riff.core.data.importer.ChartImporter
import com.rodrigomachado.riff.core.data.importer.ImportReport
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.SeparationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val isLoading: Boolean = true,
    val hasFolder: Boolean = false,
    val charts: List<Chart> = emptyList(),
    val isImporting: Boolean = false,
    val scannedFolders: Int = 0,
    val report: ImportReport? = null,
    val folderAccessLost: Boolean = false,
    val importFailed: Boolean = false,
    val matchSaved: Boolean = false,
    /** Músicas com a guitarra separada pela IA (a marca na lista). */
    val aiSeparated: Set<String> = emptySet(),
    /** "Separar a guitarra com IA?" aberta. */
    val prompt: SeparationAsk? = null,
    /** Pronto para jogar (a tela navega e avisa com onPlayHandled). */
    val play: PlayRequestLibrary? = null,
)

data class SeparationAsk(
    val chartId: String,
    val title: String,
    val difficulty: Difficulty,
    val lowMemory: Boolean,
    val busy: Boolean = false,
)

data class PlayRequestLibrary(val chartId: String, val difficulty: Difficulty)

private data class PromptState(val prompt: SeparationAsk? = null, val play: PlayRequestLibrary? = null)

private data class ImportState(
    val isImporting: Boolean = false,
    val scannedFolders: Int = 0,
    val report: ImportReport? = null,
    val folderAccessLost: Boolean = false,
    val importFailed: Boolean = false,
)

@HiltViewModel
@Suppress("TooManyFunctions") // importação, casamento com o Spotify e a pergunta da separação dividem o mesmo estado
class LibraryViewModel @Inject constructor(
    chartRepository: ChartRepository,
    private val folderStore: LibraryFolderStore,
    private val importer: ChartImporter,
    private val folderAccess: FolderAccess,
    private val matches: TrackMatchRepository,
    private val separations: AiSeparationRepository,
    private val scheduler: SeparationScheduler,
    private val probe: ChartAudioProbe,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val importState = MutableStateFlow(ImportState())
    private val matchSaved = MutableStateFlow(false)
    private val promptState = MutableStateFlow(PromptState())
    private val separation = combine(promptState, separations.separatedIds) { prompt, ids -> prompt to ids }

    val uiState: StateFlow<LibraryUiState> =
        combine(
            chartRepository.observeCharts(),
            folderStore.rootUri,
            importState,
            matchSaved,
            separation,
        ) { charts, rootUri, import, saved, (prompt, separated) ->
            LibraryUiState(
                isLoading = false,
                hasFolder = rootUri != null,
                charts = charts,
                isImporting = import.isImporting,
                scannedFolders = import.scannedFolders,
                report = import.report,
                folderAccessLost = import.folderAccessLost,
                importFailed = import.importFailed,
                matchSaved = saved,
                aiSeparated = separated,
                prompt = prompt.prompt,
                play = prompt.play,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    /** Escolha manual do mapa da música do Spotify (spec §5: salva e tem prioridade). */
    fun chooseChartFor(trackUri: String, chartId: String) {
        viewModelScope.launch {
            matches.save(trackUri, chartId, confidence = 1f, isManual = true)
            matchSaved.value = true
        }
    }

    /** Depois da dificuldade: pergunta se a música não tem guitarra separada (nem da IA, nem recusa guardada). */
    fun onDifficultyChosen(chart: Chart, difficulty: Difficulty) {
        viewModelScope.launch {
            promptState.value = if (needsAsking(chart.id)) {
                PromptState(prompt = SeparationAsk(chart.id, chart.title, difficulty, scheduler.lowMemoryDevice))
            } else {
                PromptState(play = PlayRequestLibrary(chart.id, difficulty))
            }
        }
    }

    private suspend fun needsAsking(chartId: String): Boolean {
        val running = scheduler.status.value
        if (running is SeparationStatus.Running && running.chartId == chartId) return false
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

    fun onSeparate() {
        val ask = promptState.value.prompt ?: return
        promptState.value = if (scheduler.start(ask.chartId, ask.title)) {
            PromptState()
        } else {
            PromptState(ask.copy(busy = true))
        }
    }

    fun onPlayWithoutSeparating() {
        val ask = promptState.value.prompt ?: return
        viewModelScope.launch {
            // Ocupado com outra separação, "jogar sem separar" é só por agora: a pergunta volta da próxima vez.
            if (!ask.busy) separations.decline(ask.chartId)
            promptState.value = PromptState(play = PlayRequestLibrary(ask.chartId, ask.difficulty))
        }
    }

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
