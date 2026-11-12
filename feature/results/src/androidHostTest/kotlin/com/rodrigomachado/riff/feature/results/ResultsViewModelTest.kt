package com.rodrigomachado.riff.feature.results

import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.ScoreRepository
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.ScoreRecord
import com.rodrigomachado.riff.core.ui.art.ChartArtImages
import com.rodrigomachado.riff.core.ui.art.ChartThumbImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

fun record(id: Long, score: Long) = ScoreRecord(id, "a", Difficulty.HARD, score, 0.875f, 42, 70, 10, 5, 0)

class FakeScores(private val records: List<ScoreRecord>) : ScoreRepository {
    override suspend fun save(record: ScoreRecord) = error("não usado")

    override suspend fun get(id: Long) = records.firstOrNull { it.id == id }

    override suspend fun best(chartId: String, difficulty: Difficulty) = records.maxByOrNull { it.score }

    override suspend fun bestBefore(chartId: String, difficulty: Difficulty, scoreId: Long) =
        records.filter { it.id < scoreId }.maxByOrNull { it.score }

    override suspend fun last() = records.maxByOrNull { it.playedAtEpochMs }

    override suspend fun recentBests(limit: Int) = emptyList<ScoreRecord>()

    override fun observeChartBests() = kotlinx.coroutines.flow.flowOf(emptyList<ScoreRecord>())
}

/** Ranking falso: guarda as partidas enviadas e devolve o status que o teste definir. */
class RecordingRanking(override val isConfigured: Boolean = true) :
    com.rodrigomachado.riff.core.data.ranking.RankingRepository {
    val submitted = mutableListOf<Pair<com.rodrigomachado.riff.core.model.ScoreRecord, String>>()
    val status = kotlinx.coroutines.flow.MutableStateFlow<com.rodrigomachado.riff.core.data.ranking.SubmitStatus?>(null)
    override val player = kotlinx.coroutines.flow.MutableStateFlow(
        com.rodrigomachado.riff.core.data.ranking.Player(null, null),
    )
    override val askedLater = kotlinx.coroutines.flow.MutableStateFlow(false)

    override fun askLater() = Unit

    override suspend fun register(name: String) = com.rodrigomachado.riff.core.data.ranking.NameResult.Ok

    override fun submitIfBest(record: com.rodrigomachado.riff.core.model.ScoreRecord, title: String, artist: String) {
        submitted += record to "$title — $artist"
    }

    override fun statusFor(scoreId: Long) = status

    override suspend fun flush() = Unit

    override suspend fun songs(query: String) = com.rodrigomachado.riff.core.data.ranking.ApiAnswer.Offline

    override suspend fun board(chartId: String) = com.rodrigomachado.riff.core.data.ranking.ApiAnswer.Offline

    override suspend fun linkCode() = com.rodrigomachado.riff.core.data.ranking.LinkResult.Offline

    override suspend fun link(code: String) = com.rodrigomachado.riff.core.data.ranking.LinkResult.Offline
}

class FakeCharts : ChartRepository {
    override suspend fun remove(ids: Collection<String>) = Unit

    override fun observeCharts(): Flow<List<Chart>> = flowOf(emptyList())

    override suspend fun getChart(id: String) =
        Chart(id, "Música", "Banda", null, null, null, "raiz/$id", ChartFormat.CHART, setOf(Difficulty.HARD))

    override suspend fun saveScanned(chart: Chart, scanId: Long) = Unit

    override suspend fun removeNotSeenIn(scanId: Long) = Unit

    override suspend fun keepFolder(folderUri: String, scanId: Long) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class ResultsViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val ranking = RecordingRanking()

    private fun viewModel(scoreId: Long, records: List<ScoreRecord>) = HiltResultsViewModel(
        SavedStateHandle(mapOf("scoreId" to scoreId)),
        FakeScores(records),
        FakeCharts(),
        ranking,
        ChartThumbImages(ChartArtImages { null }),
    )

    @Test
    fun highestScoreIsANewBest() = runTest {
        val state = viewModel(2, listOf(record(1, 500), record(2, 900))).uiState.value
        assertEquals(
            ResultsUiState.Ready(
                record(2, 900),
                "Música",
                "Banda",
                isNewBest = true,
                rankingOn = true,
                previousBest = 500,
            ),
            state,
        )
    }

    /** O "antes: N": o recorde de antes desta partida (as que vieram depois não contam). */
    @Test
    fun previousBestIsTheBestBeforeThisMatch() = runTest {
        val first = viewModel(1, listOf(record(1, 500))).uiState.value as ResultsUiState.Ready
        assertEquals("a primeira partida não tem recorde anterior", null, first.previousBest)

        val third = viewModel(3, listOf(record(1, 500), record(2, 700), record(3, 900))).uiState.value
        assertEquals(700L, (third as ResultsUiState.Ready).previousBest)
    }

    /** Sem recorde novo, nada de "antes". */
    @Test
    fun noPreviousBestWithoutANewRecord() = runTest {
        val state = viewModel(1, listOf(record(1, 500), record(2, 900))).uiState.value as ResultsUiState.Ready
        assertEquals(null, state.previousBest)
    }

    @Test
    fun lowerScoreIsNotANewBest() = runTest {
        val state = viewModel(1, listOf(record(1, 500), record(2, 900))).uiState.value as ResultsUiState.Ready
        assertEquals(false, state.isNewBest)
    }

    /** O envio continua depois da partida: o Resultado acompanha (enviando → posição). */
    @Test
    fun theRankingStatusFollowsTheMatch() = runTest {
        val vm = viewModel(2, listOf(record(2, 900)))
        ranking.status.value = com.rodrigomachado.riff.core.data.ranking.SubmitStatus.Sending
        ranking.status.value = com.rodrigomachado.riff.core.data.ranking.SubmitStatus.Placed(3, 27)

        assertEquals(
            com.rodrigomachado.riff.core.data.ranking.SubmitStatus.Placed(3, 27),
            (vm.uiState.value as ResultsUiState.Ready).ranking,
        )
    }

    @Test
    fun unknownResultIsMissing() = runTest {
        assertEquals(ResultsUiState.Missing, viewModel(7, emptyList()).uiState.value)
    }
}
