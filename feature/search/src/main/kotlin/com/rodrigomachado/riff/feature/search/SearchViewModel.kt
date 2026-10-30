package com.rodrigomachado.riff.feature.search

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.data.EncoreDownloads
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.download.ChartDownloadException
import com.rodrigomachado.riff.core.data.download.ChartDownloader
import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.encore.EncoreClient
import com.rodrigomachado.riff.core.encore.EncoreFormatException
import com.rodrigomachado.riff.core.encore.EncoreUnavailableException
import com.rodrigomachado.riff.core.encore.VersionTitles
import com.rodrigomachado.riff.core.model.Difficulty
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
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
    /** Falso: as gravações originais; verdadeiro: covers, remixes, ao vivo, medleys. */
    val versions: Boolean = false,
    val onlySeparateGuitar: Boolean = false,
    /** A procura de mapas com guitarra separada (só com o filtro ligado). */
    val guitarSearch: GuitarSearch? = null,
)

/** [checked] mapas conferidos de [limit] nesta procura; [running] enquanto ela continua. */
data class GuitarSearch(
    val checked: Int,
    val limit: Int,
    val running: Boolean,
    /** Falso quando o Encore não tem mais páginas e todos os resultados já foram conferidos. */
    val canSearchMore: Boolean = true,
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
@Suppress("TooManyFunctions") // busca, filtros, procura automática e download moram juntos: dividem o mesmo estado
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val encore: EncoreClient,
    private val downloader: ChartDownloader,
    private val downloads: EncoreDownloads,
    private val folderAccess: FolderAccess,
    private val folders: LibraryFolderStore,
) : ViewModel() {
    private val state = MutableStateFlow(SearchUiState(query = savedStateHandle["query"] ?: ""))
    val uiState: StateFlow<SearchUiState> = state.asStateFlow()

    // A busca confirmada: o Carregar mais e a procura automática continuam ela, mesmo com o campo editado.
    private var lastQuery = ""

    // Tudo que veio do Encore nesta busca (sem repetir), na ordem; a lista visível sai dos filtros.
    private val loaded = mutableListOf<ResultItem>()

    // Guitarra separada por md5, guardada durante a tela (null: não deu para conferir).
    private val separate = mutableMapOf<String, Boolean?>()
    private var page = 0
    private var hasMore = false
    private var guitarLimit = GUITAR_LIMIT
    private var guitarTarget = GUITAR_TARGET
    private var guitarRunning = false
    private var filling: Job? = null
    private val guitarChecks = Semaphore(MAX_PEEKS)

    init {
        viewModelScope.launch { refreshWriteAccess() }
        if (state.value.query.isNotBlank()) search()
    }

    fun onQueryChange(text: String) = state.update { it.copy(query = text) }

    fun search() {
        val query = state.value.query.trim()
        if (query.isEmpty()) return
        lastQuery = query
        loaded.clear()
        page = 0
        hasMore = false
        guitarLimit = GUITAR_LIMIT
        guitarTarget = GUITAR_TARGET
        state.update { it.copy(results = emptyList(), error = null, guitarSearch = null) }
        restart { fill() }
    }

    /** Com o filtro de guitarra, Carregar mais procura mais mapas com guitarra separada. */
    fun loadMore() {
        if (state.value.onlySeparateGuitar) {
            searchMoreWithGuitar()
        } else {
            restart { if (loadNextPage()) fill() }
        }
    }

    fun showVersions(versions: Boolean) {
        state.update { it.copy(versions = versions) }
        // Cada modo tem os seus conferidos: o limite do outro (já aumentado) não vale aqui.
        guitarTarget = GUITAR_TARGET
        guitarLimit = maxOf(GUITAR_LIMIT, guitarChecked())
        publish()
        restart { fill() }
    }

    fun onlySeparateGuitar(enabled: Boolean) {
        state.update { it.copy(onlySeparateGuitar = enabled) }
        publish()
        restart { fill() }
    }

    /** Mais [GUITAR_TARGET] encontrados ou mais [GUITAR_LIMIT] conferidos na procura por guitarra separada. */
    fun searchMoreWithGuitar() {
        guitarTarget = visible().size + GUITAR_TARGET
        guitarLimit = guitarChecked() + GUITAR_LIMIT
        restart { fill() }
    }

    /**
     * Uma procura por vez: mudar a busca ou um filtro para a anterior. A nova espera a anterior terminar de parar,
     * para os indicadores que ela desliga ao parar não apagarem os da nova.
     */
    private fun restart(block: suspend () -> Unit) {
        val previous = filling
        previous?.cancel()
        filling = viewModelScope.launch {
            previous?.join()
            block()
        }
    }

    /**
     * Completa a lista pelos filtros: sem o de guitarra, busca a próxima página se nada ficou visível; com ele,
     * confere os pacotes (e busca páginas) até [GUITAR_TARGET] encontrados ou [guitarLimit] conferidos.
     */
    private suspend fun fill() {
        // A primeira página pode ter sido interrompida por um filtro: busca de novo.
        if (page == 0 && lastQuery.isNotEmpty() && !loadNextPage()) return
        if (state.value.onlySeparateGuitar) fillWithGuitar() else fillWithoutGuitar()
    }

    /** Sem o filtro de guitarra: página vazia depois dos filtros busca a próxima; a marca é conferida nos visíveis. */
    private suspend fun fillWithoutGuitar() {
        var autoPages = 0
        while (visible().isEmpty() && hasMore && autoPages < MAX_AUTO_PAGES) {
            autoPages++
            if (!loadNextPage()) return
        }
        peekAll(visible().filter { it.chart.md5 !in separate })
    }

    /** Com o filtro: confere pacotes (e busca páginas) até [GUITAR_TARGET] encontrados ou [guitarLimit] conferidos. */
    private suspend fun fillWithGuitar() {
        guitarRunning = true
        publish()
        var pages = 0
        try {
            while (visible().size < guitarTarget && guitarChecked() < guitarLimit) {
                val pending = candidates().filter { it.chart.md5 !in separate }.take(guitarLimit - guitarChecked())
                val progressed = if (pending.isNotEmpty()) {
                    peekAll(pending)
                    true
                } else {
                    // Quando quase nada é candidato (Versões), as páginas não acabariam: um limite por procura.
                    hasMore && pages++ < MAX_AUTO_PAGES && loadNextPage()
                }
                if (!progressed) break
            }
        } finally {
            guitarRunning = false
            publish()
        }
    }

    /** Lê a próxima página do Encore; falso se não deu (o erro fica na tela). */
    private suspend fun loadNextPage(): Boolean {
        state.update { it.copy(isSearching = true) }
        return try {
            val result = encore.search(lastQuery, page + 1)
            val owned = downloads.md5s.first()
            val shown = loaded.map { it.chart.md5 }.toSet()
            // A página seguinte pode repetir mapas (a ordem do Encore muda): a lista não repete a chave.
            val fresh = result.charts.filter { it.md5 !in shown }
                .map { ResultItem(it, readyState(it, owned), separateGuitar = null) }
            // Só avança depois de ler tudo: interrompida antes, a página é pedida de novo.
            page = result.page
            hasMore = result.hasMore
            loaded += fresh
            state.update { it.copy(isSearching = false, error = null) }
            publish()
            true
        } catch (e: EncoreFormatException) {
            Log.w(TAG, "A resposta do Encore mudou", e)
            failed(SearchError.FORMAT_CHANGED)
        } catch (e: EncoreUnavailableException) {
            Log.w(TAG, "Encore indisponível", e)
            failed(SearchError.UNAVAILABLE)
        } finally {
            state.update { it.copy(isSearching = false) }
        }
    }

    private fun failed(error: SearchError): Boolean {
        state.update { it.copy(isSearching = false, error = error) }
        return false
    }

    /** No máximo [MAX_PEEKS] ao mesmo tempo; cada md5 uma vez por tela. */
    private suspend fun peekAll(items: List<ResultItem>) = coroutineScope {
        items.forEach { item ->
            launch {
                separate[item.chart.md5] = guitarChecks.withPermit { encore.hasSeparateGuitar(item.chart) }
                publish()
            }
        }
    }

    private fun candidates() = loaded.filter { VersionTitles.isVersion(it.chart.name) == state.value.versions }

    /** Conferidos entre os candidatos desta busca (os já conferidos para a marca contam). */
    private fun guitarChecked() = candidates().count { it.chart.md5 in separate }

    private fun visible() = candidates()
        .filter { !state.value.onlySeparateGuitar || separate[it.chart.md5] == true }
        .map { it.copy(separateGuitar = separate[it.chart.md5]) }

    private fun publish() = state.update { s ->
        val results = visible()
        // Busca terminada sem nada visível (o filtro de guitarra tem a mensagem dele).
        val finished = page > 0 && !hasMore && !s.isSearching
        val nothing = finished && results.isEmpty() && !s.onlySeparateGuitar
        val canSearchMore = hasMore || candidates().any { it.chart.md5 !in separate }
        s.copy(
            results = results,
            // Com o filtro de guitarra, o Carregar mais só aparece parado e com resultados (sem eles, o Procurar mais).
            hasMore = if (s.onlySeparateGuitar) canSearchMore && !guitarRunning && results.isNotEmpty() else hasMore,
            error = s.error ?: if (nothing) SearchError.NOTHING_FOUND else null,
            guitarSearch = if (s.onlySeparateGuitar) {
                GuitarSearch(
                    guitarChecked(),
                    guitarLimit,
                    guitarRunning,
                    canSearchMore,
                )
            } else {
                null
            },
        )
    }

    private suspend fun readyState(chart: EncoreChart, owned: Set<String>): ItemState {
        if (chart.md5 !in owned) return ItemState.Available
        val id = downloads.chartIdOf(chart.md5) ?: return ItemState.Available
        return ItemState.Ready(id, chart.guitarDifficulties)
    }

    fun download(chart: EncoreChart) {
        if (!state.value.canWrite) return
        viewModelScope.launch {
            setItem(chart.md5, ItemState.Downloading(0f))
            try {
                val imported = downloader.download(chart, null) { setItem(chart.md5, ItemState.Downloading(it)) }
                setItem(chart.md5, ItemState.Ready(imported.id, imported.difficulties))
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

    private fun setItem(md5: String, item: ItemState) {
        val at = loaded.indexOfFirst { it.chart.md5 == md5 }
        if (at >= 0) loaded[at] = loaded[at].copy(state = item)
        publish()
    }

    private companion object {
        const val TAG = "RiffSearch"
        const val MAX_PEEKS = 3
        const val GUITAR_TARGET = 10
        const val GUITAR_LIMIT = 100
        const val MAX_AUTO_PAGES = 5
    }
}
