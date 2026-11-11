package com.rodrigomachado.riff.feature.home

import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.ScoreRepository
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.ScoreRecord
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

/** Partidas falsas: a última é a de [playedAtEpochMs] maior; os recordes recentes vêm prontos. */
private class FakeScores(val records: MutableList<ScoreRecord>, val bests: List<ScoreRecord>) : ScoreRepository {
    var recentLimit = 0

    override suspend fun save(record: ScoreRecord) = error("não usado")

    override suspend fun get(id: Long) = records.firstOrNull { it.id == id }

    override suspend fun best(chartId: String, difficulty: Difficulty) =
        records.filter { it.chartId == chartId && it.difficulty == difficulty }.maxByOrNull { it.score }

    override suspend fun last() = records.maxByOrNull { it.playedAtEpochMs }

    override suspend fun recentBests(limit: Int): List<ScoreRecord> {
        recentLimit = limit
        return bests.take(limit)
    }

    override fun observeChartBests() = kotlinx.coroutines.flow.flowOf(emptyList<ScoreRecord>())
}

/** A biblioteca falsa: só os mapas de [ids]. */
private class FakeCharts(vararg val ids: String) : ChartRepository {
    override fun observeCharts(): Flow<List<Chart>> = flowOf(emptyList())

    override suspend fun getChart(id: String) = if (id in ids) {
        Chart(id, "Título $id", "Artista $id", null, null, null, "raiz/$id", ChartFormat.CHART, setOf(Difficulty.HARD))
    } else {
        null
    }

    override suspend fun saveScanned(chart: Chart, scanId: Long) = Unit

    override suspend fun remove(ids: Collection<String>) = Unit

    override suspend fun removeNotSeenIn(scanId: Long) = Unit

    override suspend fun keepFolder(folderUri: String, scanId: Long) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun record(id: Long, chartId: String, score: Long, at: Long, difficulty: Difficulty = Difficulty.HARD) =
        ScoreRecord(id, chartId, difficulty, score, 0.9f, 1, 1, 0, 0, at)

    /** "Continuar": a última partida, com o título e o artista do mapa e o recorde daquele mapa e dificuldade. */
    @Test
    fun continueIsTheLastMatchWithItsRecord() = runTest {
        val records = mutableListOf(record(1, "a", 900, at = 1), record(2, "a", 300, at = 5))
        val vm = HomeViewModel(FakeScores(records, emptyList()), FakeCharts("a"), images = null)

        assertEquals(HomeMatch("a", "Título a", "Artista a", Difficulty.HARD, best = 900), vm.uiState.value.last)
    }

    @Test
    fun withoutMatchesThereIsNothingToShow() = runTest {
        val vm = HomeViewModel(FakeScores(mutableListOf(), emptyList()), FakeCharts("a"), images = null)

        assertEquals(HomeUiState(), vm.uiState.value)
    }

    /** O mapa da última partida saiu da biblioteca: o "Continuar" some (não há o que abrir). */
    @Test
    fun continueWithADeletedChartIsHidden() = runTest {
        val records = mutableListOf(record(1, "gone", 900, at = 1))
        val vm = HomeViewModel(FakeScores(records, records), FakeCharts("a"), images = null)

        assertEquals(HomeUiState(), vm.uiState.value)
    }

    /** "Últimos recordes": até 3, com o título do mapa e os pontos. */
    @Test
    fun recentBestsAreUpToThreeWithTheirTitles() = runTest {
        val bests = listOf(record(4, "d", 40, 9), record(3, "c", 30, 8), record(2, "b", 20, 7), record(1, "a", 10, 6))
        val scores = FakeScores(bests.toMutableList(), bests)
        val vm = HomeViewModel(scores, FakeCharts("a", "b", "c", "d"), images = null)

        assertEquals(HomeViewModel.RECENT_BESTS, scores.recentLimit)
        assertEquals(
            listOf("Título d" to 40L, "Título c" to 30L, "Título b" to 20L),
            vm.uiState.value.recentBests.map { it.title to it.best },
        )
    }

    /** Voltar à tela inicial depois de uma partida relê o banco: o "Continuar" já é a partida nova. */
    @Test
    fun refreshPicksTheNewMatch() = runTest {
        val records = mutableListOf(record(1, "a", 900, at = 1))
        val vm = HomeViewModel(FakeScores(records, emptyList()), FakeCharts("a", "b"), images = null)

        records += record(2, "b", 500, at = 2)
        vm.refresh()

        assertEquals("b", vm.uiState.value.last?.chartId)
    }
}
