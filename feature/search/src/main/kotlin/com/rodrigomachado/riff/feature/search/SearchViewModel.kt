package com.rodrigomachado.riff.feature.search

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.chart.match.TrackMatcher
import com.rodrigomachado.riff.core.chart.match.TrackQuery
import com.rodrigomachado.riff.core.data.EncoreDownloads
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.download.ChartDownloadException
import com.rodrigomachado.riff.core.data.download.ChartDownloader
import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.encore.EncoreClient
import com.rodrigomachado.riff.core.encore.EncoreFormatException
import com.rodrigomachado.riff.core.encore.EncoreUnavailableException
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<ResultItem> = emptyList(),
    val isSearching: Boolean = false,
    val hasMore: Boolean = false,
    val error: SearchError? = null,
    val canWrite: Boolean = true,
    val folderUri: String? = null,
    val matched: Boolean = false,
)

data class ResultItem(val chart: EncoreChart, val state: ItemState, val separateGuitar: Boolean?)

sealed interface ItemState {
    data object Available : ItemState

    data class Downloading(val progress: Float) : ItemState

    /** Na biblioteca: Jogar com estas dificuldades. */
    data class Ready(val chartId: String, val difficulties: Set<Difficulty>) : ItemState

    data class Failed(val reason: ChartDownloadException.Reason) : ItemState
}

enum class SearchError { UNAVAILABLE, FORMAT_CHANGED, NOTHING_FOUND }

@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val encore: EncoreClient,
    private val downloader: ChartDownloader,
    private val downloads: EncoreDownloads,
    private val folderAccess: FolderAccess,
    private val folders: LibraryFolderStore,
) : ViewModel() {
    private val matchTrackUri: String? = savedStateHandle["matchTrackUri"]
    private val track: TrackQuery? = savedStateHandle.get<String>("matchTrackTitle")?.let { title ->
        TrackQuery(title, savedStateHandle["matchTrackArtist"] ?: "", savedStateHandle["matchTrackDurationMs"])
    }
    private val state = MutableStateFlow(SearchUiState(query = savedStateHandle["query"] ?: ""))
    val uiState: StateFlow<SearchUiState> = state.asStateFlow()

    private var page = 1
    private var searching: Job? = null
    private val guitarChecks = Semaphore(MAX_PEEKS)
    private val checked = mutableSetOf<String>()

    init {
        viewModelScope.launch { refreshWriteAccess() }
        if (state.value.query.isNotBlank()) search()
    }

    fun onQueryChange(text: String) = state.update { it.copy(query = text) }

    fun search() {
        val query = state.value.query.trim()
        if (query.isNotEmpty()) load(query, append = false)
    }

    fun loadMore() = load(state.value.query.trim(), append = true)

    private fun load(query: String, append: Boolean) {
        searching?.cancel()
        searching = viewModelScope.launch {
            state.update { it.copy(isSearching = true, error = null) }
            try {
                val result = encore.search(query, if (append) page + 1 else 1)
                page = result.page
                val owned = downloads.md5s.first()
                val items = ordered(
                    result.charts,
                    track,
                ).map { ResultItem(it, readyState(it, owned), separateGuitar = null) }
                state.update {
                    it.copy(
                        results = if (append) it.results + items else items,
                        hasMore = result.hasMore,
                        isSearching = false,
                        error = if (!append && items.isEmpty()) SearchError.NOTHING_FOUND else null,
                    )
                }
                items.forEach { checkSeparateGuitar(it.chart) }
            } catch (e: EncoreFormatException) {
                Log.w(TAG, "A resposta do Encore mudou", e)
                state.update { it.copy(isSearching = false, error = SearchError.FORMAT_CHANGED) }
            } catch (e: EncoreUnavailableException) {
                Log.w(TAG, "Encore indisponível", e)
                state.update { it.copy(isSearching = false, error = SearchError.UNAVAILABLE) }
            }
        }
    }

    private suspend fun readyState(chart: EncoreChart, owned: Set<String>): ItemState {
        if (chart.md5 !in owned) return ItemState.Available
        val id = downloads.chartIdOf(chart.md5) ?: return ItemState.Available
        return ItemState.Ready(id, chart.guitarDifficulties)
    }

    /** No máximo 3 ao mesmo tempo; cada md5 uma vez por tela. A marca chega depois dos resultados. */
    private fun checkSeparateGuitar(chart: EncoreChart) {
        if (!checked.add(chart.md5)) return
        viewModelScope.launch {
            val separate = guitarChecks.withPermit { encore.hasSeparateGuitar(chart) } ?: return@launch
            state.update { s ->
                s.copy(
                    results = s.results.map {
                        if (it.chart.md5 == chart.md5) {
                            it.copy(separateGuitar = separate)
                        } else {
                            it
                        }
                    },
                )
            }
        }
    }

    fun download(chart: EncoreChart) {
        if (!state.value.canWrite) return
        viewModelScope.launch {
            setItem(chart.md5, ItemState.Downloading(0f))
            try {
                val imported = downloader.download(
                    chart,
                    matchTrackUri,
                ) { setItem(chart.md5, ItemState.Downloading(it)) }
                setItem(chart.md5, ItemState.Ready(imported.id, imported.difficulties))
                if (matchTrackUri != null) state.update { it.copy(matched = true) }
            } catch (e: ChartDownloadException) {
                Log.w(TAG, "Download falhou: ${e.reason}", e)
                setItem(chart.md5, ItemState.Failed(e.reason))
            }
        }
    }

    /** O jogador escolheu a pasta de novo no seletor, agora com permissão de gravar. */
    fun onWriteAccessGranted(uri: String) {
        folderAccess.persistAccess(uri)
        viewModelScope.launch {
            if (folders.rootUri.first() != uri) folders.setRootUri(uri)
            refreshWriteAccess()
        }
    }

    private suspend fun refreshWriteAccess() {
        val root = folders.rootUri.first()
        state.update { it.copy(folderUri = root, canWrite = root != null && folderAccess.canWrite(root)) }
    }

    private fun setItem(md5: String, item: ItemState) = state.update { s ->
        s.copy(results = s.results.map { if (it.chart.md5 == md5) it.copy(state = item) else it })
    }

    private companion object {
        const val TAG = "RiffSearch"
        const val MAX_PEEKS = 3
    }
}

/** Vindo do Spotify, os mais parecidos com a faixa primeiro (a mesma nota do casamento automático). */
private fun ordered(charts: List<EncoreChart>, track: TrackQuery?): List<EncoreChart> {
    val query = track ?: return charts
    return charts.sortedByDescending { TrackMatcher.score(query, it.asChart()) }
}

private fun EncoreChart.asChart() =
    Chart(md5, name, artist, null, charter, durationMs, "", ChartFormat.CHART, guitarDifficulties)
