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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

class FakeTransport : HttpTransport {
    var postBody: String? = null
    var failure: IOException? = null
    var rangeBody: ByteArray? = null
    var lastJson: String? = null

    override suspend fun post(url: String, json: String): HttpResponse {
        failure?.let { throw it }
        lastJson = json
        return HttpResponse(201, null, checkNotNull(postBody).byteInputStream())
    }

    override suspend fun get(url: String, range: LongRange?): HttpResponse {
        val body = rangeBody ?: return HttpResponse(404, null, "".byteInputStream())
        return HttpResponse(206, null, body.inputStream())
    }
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
                route.matchTrackUri?.let { put("matchTrackUri", it) }
                route.matchTrackTitle?.let { put("matchTrackTitle", it) }
                route.matchTrackArtist?.let { put("matchTrackArtist", it) }
                route.matchTrackDurationMs?.let { put("matchTrackDurationMs", it) }
            },
        ),
        EncoreClient(transport, Dispatchers.Main),
        downloader,
        downloads,
        access,
        folders,
    )

    @Test
    fun fromTheSpotifyScreenItSearchesRightAwayAndPutsTheClosestFirst() = runTest {
        val vm = viewModel(
            SearchRoute(
                query = "Nirvana Smells Like Teen Spirit",
                matchTrackUri = "spotify:track:x",
                matchTrackTitle = "Smells Like Teen Spirit",
                matchTrackArtist = "Nirvana",
                matchTrackDurationMs = 301_000,
            ),
        )
        advanceUntilIdle()

        assertEquals("Nirvana Smells Like Teen Spirit", vm.uiState.value.query)
        assertEquals("Smells Like Teen Spirit", vm.uiState.value.results.first().chart.name)
        assertEquals(2, vm.uiState.value.results.size)
    }

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
    fun downloadingFromSpotifyMatchesAndReturns() = runTest {
        val vm = viewModel(SearchRoute(query = "teen spirit", matchTrackUri = "spotify:track:x", matchTrackTitle = "x"))
        advanceUntilIdle()

        vm.download(vm.uiState.value.results.first().chart)
        advanceUntilIdle()

        assertEquals("spotify:track:x", downloader.lastMatchTrackUri)
        assertEquals(true, vm.uiState.value.matched)
    }

    @Test
    fun withoutWriteAccessResultsShowButDownloadExplains() = runTest {
        access.writable = false
        val vm = viewModel(SearchRoute(query = "teen spirit"))
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.canWrite)
        assertEquals(2, vm.uiState.value.results.size)
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
    fun loadMoreAppendsTheNextPage() = runTest {
        transport.postBody = fixture.replace("\"found\": 8", "\"found\": 60")
        val vm = viewModel(SearchRoute(query = "teen spirit"))
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.hasMore)

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(2, JSONObject(checkNotNull(transport.lastJson)).getInt("page"))
        assertEquals(4, vm.uiState.value.results.size) // a mesma página de novo, só para o teste
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
}
