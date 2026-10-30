package com.rodrigomachado.riff.feature.search

import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.chart.sng.SngWriter
import com.rodrigomachado.riff.core.data.EncoreDownloads
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.download.ChartDownloader
import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.encore.EncoreClient
import com.rodrigomachado.riff.core.encore.HttpResponse
import com.rodrigomachado.riff.core.encore.HttpTransport
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

class FakeTransport : HttpTransport {
    var postBody: String? = null

    /** Corpo de cada página (a página pedida vem no JSON); vazio usa [postBody]. */
    var pages: List<String> = emptyList()

    /** md5 dos pacotes com guitarra separada; os outros só têm song.opus. */
    var stems: Set<String> = emptySet()
    var failure: IOException? = null
    var failFromPage: Int? = null
    var rangeBody: ByteArray? = null
    var peekGate: CompletableDeferred<Unit>? = null
    var searchGate: CompletableDeferred<Unit>? = null
    var lastJson: String? = null
    var searches = 0
    var peeks = 0

    override suspend fun post(url: String, json: String): HttpResponse {
        searchGate?.await()
        failure?.let { throw it }
        val page = JSONObject(json).getInt("page")
        failFromPage?.let { if (page >= it) throw java.net.UnknownHostException("api.enchor.us") }
        lastJson = json
        searches++
        val body = if (pages.isEmpty()) checkNotNull(postBody) else pages.getOrElse(page - 1) { EMPTY_PAGE }
        return HttpResponse(201, null, body.byteInputStream())
    }

    override suspend fun get(url: String, range: LongRange?): HttpResponse {
        peeks++
        peekGate?.await()
        rangeBody?.let { return HttpResponse(206, null, it.inputStream()) }
        if (pages.isEmpty()) return HttpResponse(404, null, "".byteInputStream())
        val md5 = url.substringAfterLast('/').substringBefore('.').substringBefore('_')
        val files = if (md5 in stems) {
            mapOf(
                "guitar.ogg" to ByteArray(4),
                "song.ogg" to ByteArray(4),
            )
        } else {
            mapOf("song.opus" to ByteArray(4))
        }
        return HttpResponse(206, null, SngWriter.write(mapOf("name" to md5), files).inputStream())
    }

    private companion object {
        const val EMPTY_PAGE = """{"found": 0, "out_of": 0, "page": 1, "data": []}"""
    }
}

private val STEMS = mapOf("guitar.ogg" to ByteArray(4), "song.ogg" to ByteArray(4))
private val SINGLE = mapOf("song.opus" to ByteArray(4))

/** Um resultado do Encore para as páginas falsas. */
fun c(md5: String, name: String) = md5 to name

/** Página no formato da resposta do Encore, com mapas de guitarra (Expert) da comunidade. */
fun page(vararg charts: Pair<String, String>, found: Int): String {
    val data = charts.joinToString(",") { (md5, name) ->
        """{"name": "$name", "artist": "Banda", "charter": "charter", "icon": "", "diff_guitar": 2,
            "md5": "$md5", "hasVideoBackground": false, "song_length": 200000,
            "notesData": {"noteCounts": [{"instrument": "guitar", "difficulty": "expert", "count": 100}]}}"""
    }
    return """{"found": $found, "out_of": $found, "page": 1, "data": [$data]}"""
}

class FakeDownloader : ChartDownloader {
    var progressSteps = emptyList<Float>()
    var gate: CompletableDeferred<Unit>? = null
    var calls = 0
    var lastMatchTrackUri: String? = null

    override suspend fun download(chart: EncoreChart, matchTrackUri: String?, onProgress: (Float) -> Unit): Chart {
        calls++
        lastMatchTrackUri = matchTrackUri
        progressSteps.forEach(onProgress)
        gate?.await()
        val id = "baixado-${chart.md5}"
        return Chart(id, chart.name, chart.artist, null, null, null, "u", ChartFormat.CHART, setOf(Difficulty.EXPERT))
    }
}

class FakeEncoreDownloads : EncoreDownloads {
    val owned = mutableMapOf<String, String>()
    override val md5s get() = flowOf(owned.keys.toSet())

    override suspend fun remember(chartId: String, md5: String) {
        owned[md5] = chartId
    }

    override suspend fun chartIdOf(md5: String) = owned[md5]
}

class FakeFolderAccess : FolderAccess {
    var writable = true
    val persisted = mutableListOf<String>()

    override fun persistAccess(folderUri: String) {
        persisted += folderUri
    }

    override fun canWrite(folderUri: String) = writable
}

class FakeFolderStore : LibraryFolderStore {
    override val rootUri = MutableStateFlow<String?>("root")

    override suspend fun setRootUri(uri: String) {
        rootUri.value = uri
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchViewModelTest {
    private val fixture = checkNotNull(javaClass.getResource("/encore/search_teen_spirit.json")).readText()
    private val transport = FakeTransport().also { it.postBody = fixture }
    private val downloader = FakeDownloader()
    private val downloads = FakeEncoreDownloads()
    private val access = FakeFolderAccess()
    private val folders = FakeFolderStore()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(route: SearchRoute) = SearchViewModel(
        SavedStateHandle(
            buildMap {
                route.query?.let { put("query", it) }
            },
        ),
        EncoreClient(transport, Dispatchers.Main),
        downloader,
        downloads,
        access,
        folders,
    )

    @Test
    fun fromTheLibraryNothingIsSearchedUntilTheQueryIsConfirmed() = runTest {
        val vm = viewModel(SearchRoute())
        advanceUntilIdle()
        assertEquals(null, transport.lastJson)

        vm.onQueryChange("teen spirit")
        vm.search()
        advanceUntilIdle()

        assertEquals("teen spirit", JSONObject(checkNotNull(transport.lastJson)).getString("search"))
    }

    @Test
    fun downloadingShowsProgressAndThenReady() = runTest {
        downloader.progressSteps = listOf(0.5f)
        val vm = viewModel(SearchRoute(query = "teen spirit"))
        advanceUntilIdle()
        val chart = vm.uiState.value.results.first().chart
        downloader.gate = CompletableDeferred()

        vm.download(chart)
        runCurrent()
        assertEquals(ItemState.Downloading(0.5f), vm.uiState.value.results.first().state)

        checkNotNull(downloader.gate).complete(Unit)
        advanceUntilIdle()
        assertEquals(
            ItemState.Ready("baixado-${chart.md5}", setOf(Difficulty.EXPERT)),
            vm.uiState.value.results.first().state,
        )
    }

    @Test
    fun aChartAlreadyInTheLibraryIsReadyWithoutDownloading() = runTest {
        downloads.owned["a07d209baccd2d58042d81b869a0077e"] = "ja-tenho"
        val vm = viewModel(SearchRoute(query = "teen spirit"))
        advanceUntilIdle()

        assertEquals("ja-tenho", (vm.uiState.value.results.first().state as ItemState.Ready).chartId)
        assertEquals(0, downloader.calls)
    }

    @Test
    fun withoutWriteAccessResultsShowButDownloadExplains() = runTest {
        access.writable = false
        val vm = viewModel(SearchRoute(query = "teen spirit"))
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.canWrite)
        assertEquals(4, vm.uiState.value.results.size) // com os oficiais
        vm.download(vm.uiState.value.results.first().chart)
        assertEquals(0, downloader.calls)

        access.writable = true
        vm.onWriteAccessGranted("root")
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.canWrite)
        assertEquals(listOf("root"), access.persisted)
    }

    @Test
    fun networkFailureAndAChangedApiHaveTheirOwnMessages() = runTest {
        transport.failure = java.net.UnknownHostException("api.enchor.us")
        val offline = viewModel(SearchRoute(query = "x"))
        advanceUntilIdle()
        assertEquals(SearchError.UNAVAILABLE, offline.uiState.value.error)

        transport.failure = null
        transport.postBody = """{"results": []}"""
        val changed = viewModel(SearchRoute(query = "x"))
        advanceUntilIdle()
        assertEquals(SearchError.FORMAT_CHANGED, changed.uiState.value.error)
    }

    @Test
    fun anEmptyResultSaysNothingWasFound() = runTest {
        transport.postBody = """{"found": 0, "out_of": 0, "page": 1, "data": []}"""
        val vm = viewModel(SearchRoute(query = "xyz"))
        advanceUntilIdle()

        assertEquals(SearchError.NOTHING_FOUND, vm.uiState.value.error)
    }

    @Test
    fun loadMoreContinuesTheConfirmedSearchWithoutRepeatingResults() = runTest {
        transport.postBody = fixture.replace("\"found\": 8", "\"found\": 60")
        val vm = viewModel(SearchRoute(query = "teen spirit"))
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.hasMore)
        vm.onQueryChange("outra coisa") // editou o campo sem pesquisar

        vm.loadMore()
        advanceUntilIdle()

        val sent = JSONObject(checkNotNull(transport.lastJson))
        assertEquals("teen spirit" to 2, sent.getString("search") to sent.getInt("page"))
        // A página seguinte repetiu os mesmos mapas: a lista não pode ter a mesma chave duas vezes.
        assertEquals(4, vm.uiState.value.results.size) // com os oficiais
    }

    @Test
    fun theSeparateGuitarMarkArrivesAfterTheResults() = runTest {
        transport.rangeBody = SngWriter.write(
            mapOf("name" to "M"),
            mapOf("guitar.ogg" to ByteArray(4), "song.ogg" to ByteArray(4)),
        )
        val vm = viewModel(SearchRoute(query = "teen spirit"))
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.results.first().separateGuitar)
    }

    @Test
    fun originalsHideVersionsAndVersionsShowOnlyThem() = runTest {
        transport.pages = listOf(page(c("a", "Enter Sandman"), c("b", "Enter Sandman (Metallica cover)"), found = 2))
        val vm = viewModel(SearchRoute(query = "sandman"))
        advanceUntilIdle()
        assertEquals(listOf("a"), vm.uiState.value.results.map { it.chart.md5 })

        vm.showVersions(true)

        assertEquals(listOf("b"), vm.uiState.value.results.map { it.chart.md5 })
    }

    @Test
    fun aPageEmptyAfterTheFiltersFetchesTheNextOne() = runTest {
        transport.pages = listOf(page(c("a", "X (cover)"), found = 50), page(c("b", "X"), found = 50))
        val vm = viewModel(SearchRoute(query = "x"))
        advanceUntilIdle()

        assertEquals(listOf("b"), vm.uiState.value.results.map { it.chart.md5 })
    }

    @Test
    fun theGuitarFilterKeepsFetchingUntilItFindsTenOrChecksAHundred() = runTest {
        // 4 páginas de 25: só 1 mapa com guitarra separada em cada.
        transport.pages = (0 until 4).map { p -> page(*Array(25) { i -> c("m$p-$i", "Música $p $i") }, found = 200) }
        transport.stems = setOf("m0-3", "m1-7", "m2-1", "m3-9")
        val vm = viewModel(SearchRoute(query = "x"))
        advanceUntilIdle()

        vm.onlySeparateGuitar(true)
        advanceUntilIdle()

        assertEquals(setOf("m0-3", "m1-7", "m2-1", "m3-9"), vm.uiState.value.results.map { it.chart.md5 }.toSet())
        assertEquals(GuitarSearch(checked = 100, limit = 100, running = false), vm.uiState.value.guitarSearch)
        assertEquals(4, transport.searches)
    }

    @Test
    fun theGuitarFilterStopsAtTenFound() = runTest {
        transport.pages = (0 until 4).map { p -> page(*Array(25) { i -> c("m$p-$i", "Música $p $i") }, found = 200) }
        transport.stems = (0 until 12).map { "m0-$it" }.toSet()
        val vm = viewModel(SearchRoute(query = "x"))
        advanceUntilIdle()

        vm.onlySeparateGuitar(true)
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.results.size >= 10)
        assertEquals(1, transport.searches) // achou na primeira página, sem buscar mais
    }

    /** Em Versões quase nada passa: sem limite de páginas, a procura buscaria a busca inteira atrás de candidatos. */
    @Test
    fun theGuitarFilterFetchesALimitedNumberOfPagesPerSearch() = runTest {
        transport.pages = (0 until 30).map { p -> page(*Array(25) { i -> c("m$p-$i", "Música $p $i") }, found = 750) }
        val vm = viewModel(SearchRoute(query = "x"))
        advanceUntilIdle()
        vm.showVersions(true) // nenhum título de versão: nada é candidato
        advanceUntilIdle()
        val before = transport.searches

        vm.onlySeparateGuitar(true)
        advanceUntilIdle()

        assertTrue(transport.searches - before <= 5)
        assertEquals(false, vm.uiState.value.guitarSearch?.running)
    }

    @Test
    fun theGuitarFilterStopsCheckingSoonAfterTheTenth() = runTest {
        transport.pages = listOf(page(*Array(25) { i -> c("m$i", "Música $i") }, found = 25))
        transport.stems = (0 until 25).map { "m$it" }.toSet()
        val vm = viewModel(SearchRoute(query = "x"))
        advanceUntilIdle()
        val before = transport.peeks

        vm.onlySeparateGuitar(true)
        advanceUntilIdle()

        assertTrue(transport.peeks - before < 25) // não confere o lote inteiro depois do 10º
    }

    @Test
    fun switchingModesWithTheGuitarFilterCountsThatModeAgainstAFreshHundred() = runTest {
        transport.pages = (0 until 8).map { p -> page(*Array(25) { i -> c("m$p-$i", "M $p $i") }, found = 200) }
        val vm = viewModel(SearchRoute(query = "x"))
        advanceUntilIdle()
        vm.onlySeparateGuitar(true)
        advanceUntilIdle()
        vm.searchMoreWithGuitar() // 200 conferidos em Originais
        advanceUntilIdle()

        vm.showVersions(true)
        advanceUntilIdle()

        assertEquals(100, vm.uiState.value.guitarSearch?.limit)
    }

    @Test
    fun searchMoreChecksAnotherHundred() = runTest {
        transport.pages = (0 until 8).map { p -> page(*Array(25) { i -> c("m$p-$i", "M $p $i") }, found = 200) }
        val vm = viewModel(SearchRoute(query = "x"))
        advanceUntilIdle()
        vm.onlySeparateGuitar(true)
        advanceUntilIdle()

        vm.searchMoreWithGuitar()
        advanceUntilIdle()

        assertEquals(200, vm.uiState.value.guitarSearch?.checked)
    }

    @Test
    fun theMarkIsRememberedAndNotCheckedAgain() = runTest {
        transport.pages = listOf(page(c("a", "A"), c("b", "B"), found = 2))
        transport.stems = setOf("a")
        val vm = viewModel(SearchRoute(query = "x"))
        advanceUntilIdle()
        val peeks = transport.peeks

        vm.onlySeparateGuitar(true)
        advanceUntilIdle()
        vm.showVersions(true)
        vm.showVersions(false)
        advanceUntilIdle()

        assertEquals(listOf("a"), vm.uiState.value.results.map { it.chart.md5 })
        assertEquals(peeks, transport.peeks) // os dois já tinham sido conferidos pela marca
    }

    @Test
    fun aNewSearchStopsTheOldOne() = runTest {
        transport.pages = (0 until 4).map { p -> page(*Array(25) { i -> c("m$p-$i", "M $p $i") }, found = 200) }
        val vm = viewModel(SearchRoute(query = "x"))
        advanceUntilIdle()
        transport.peekGate = CompletableDeferred() // segura as conferências
        vm.onlySeparateGuitar(true)
        runCurrent()

        vm.onQueryChange("y")
        transport.pages = listOf(page(c("z", "Z"), found = 1))
        vm.search()
        checkNotNull(transport.peekGate).complete(Unit)
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.results.all { it.chart.md5 == "z" })
    }

    @Test
    fun encoreGoingDownMidSearchKeepsWhatWasFound() = runTest {
        transport.pages = listOf(page(*Array(25) { i -> c("m$i", "M $i") }, found = 100))
        transport.stems = setOf("m2")
        transport.failFromPage = 2
        val vm = viewModel(SearchRoute(query = "x"))
        advanceUntilIdle()

        vm.onlySeparateGuitar(true)
        advanceUntilIdle()

        assertEquals(listOf("m2"), vm.uiState.value.results.map { it.chart.md5 })
        assertEquals(SearchError.UNAVAILABLE, vm.uiState.value.error)
        assertEquals(false, vm.uiState.value.guitarSearch?.running)
    }

    @Test
    fun aFilterChangedWhileThePageLoadsStillShowsTheResults() = runTest {
        transport.pages = listOf(page(c("a", "A"), c("v", "A (Live)"), found = 2))
        transport.searchGate = CompletableDeferred() // segura a primeira página
        val vm = viewModel(SearchRoute(query = "x"))
        runCurrent()

        vm.showVersions(true)
        checkNotNull(transport.searchGate).complete(Unit)
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isSearching)
        assertEquals(listOf("v"), vm.uiState.value.results.map { it.chart.md5 })
    }

    @Test
    fun aNewSearchThatFailsDoesNotLeaveTheGuitarSearchRunning() = runTest {
        transport.pages = (0 until 4).map { p -> page(*Array(25) { i -> c("m$p-$i", "M $p $i") }, found = 200) }
        val vm = viewModel(SearchRoute(query = "x"))
        advanceUntilIdle()
        transport.peekGate = CompletableDeferred() // a procura fica no meio
        vm.onlySeparateGuitar(true)
        runCurrent()

        transport.failFromPage = 1
        vm.search()
        advanceUntilIdle()

        assertEquals(SearchError.UNAVAILABLE, vm.uiState.value.error)
        assertEquals(false, vm.uiState.value.guitarSearch?.running)
        assertEquals(false, vm.uiState.value.isSearching)
    }

    @Test
    fun loadMoreWithTheGuitarFilterLooksForMoreMaps() = runTest {
        transport.pages = (0 until 4).map { p -> page(*Array(25) { i -> c("m$p-$i", "M $p $i") }, found = 200) }
        transport.stems = (0 until 12).map { "m0-$it" }.toSet() + "m2-5"
        val vm = viewModel(SearchRoute(query = "x"))
        advanceUntilIdle()
        vm.onlySeparateGuitar(true)
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(true, "m2-5" in vm.uiState.value.results.map { it.chart.md5 })
    }

    @Test
    fun whenTheSearchRanOutThereIsNothingMoreToLookFor() = runTest {
        transport.pages = listOf(page(c("a", "A"), c("b", "B"), found = 2))
        val vm = viewModel(SearchRoute(query = "x"))
        advanceUntilIdle()

        vm.onlySeparateGuitar(true)
        advanceUntilIdle()

        assertEquals(GuitarSearch(2, 100, running = false, canSearchMore = false), vm.uiState.value.guitarSearch)
        assertEquals(false, vm.uiState.value.hasMore)
    }

    @Test
    fun whileTheGuitarSearchRunsThereIsNoLoadMore() = runTest {
        transport.pages = (0 until 4).map { p -> page(*Array(25) { i -> c("m$p-$i", "M $p $i") }, found = 200) }
        val vm = viewModel(SearchRoute(query = "x"))
        advanceUntilIdle()
        transport.peekGate = CompletableDeferred() // a procura fica no meio
        vm.onlySeparateGuitar(true)
        runCurrent()

        assertEquals(true, vm.uiState.value.guitarSearch?.running)
        assertEquals(false, vm.uiState.value.hasMore)
        checkNotNull(transport.peekGate).complete(Unit)
    }

    @Test
    fun nothingFoundWithMoreToCheckOffersOnlySearchMore() = runTest {
        transport.pages = (0 until 8).map { p -> page(*Array(25) { i -> c("m$p-$i", "M $p $i") }, found = 200) }
        val vm = viewModel(SearchRoute(query = "x"))
        advanceUntilIdle()

        vm.onlySeparateGuitar(true)
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.guitarSearch?.canSearchMore)
        assertEquals(false, vm.uiState.value.hasMore) // o Procurar mais da mensagem basta
    }
}
