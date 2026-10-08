package com.rodrigomachado.riff.feature.results

import androidx.lifecycle.SavedStateHandle
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

fun record(id: Long, score: Long) = ScoreRecord(id, "a", Difficulty.HARD, score, 0.875f, 42, 70, 10, 5, 0)

class FakeScores(private val records: List<ScoreRecord>) : ScoreRepository {
    override suspend fun save(record: ScoreRecord) = error("não usado")

    override suspend fun get(id: Long) = records.firstOrNull { it.id == id }

    override suspend fun best(chartId: String, difficulty: Difficulty) = records.maxByOrNull { it.score }
}

class FakeCharts : ChartRepository {
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

    private fun viewModel(scoreId: Long, records: List<ScoreRecord>) =
        ResultsViewModel(SavedStateHandle(mapOf("scoreId" to scoreId)), FakeScores(records), FakeCharts())

    @Test
    fun highestScoreIsANewBest() = runTest {
        val state = viewModel(2, listOf(record(1, 500), record(2, 900))).uiState.value
        assertEquals(ResultsUiState.Ready(record(2, 900), "Música", "Banda", isNewBest = true), state)
    }

    @Test
    fun lowerScoreIsNotANewBest() = runTest {
        val state = viewModel(1, listOf(record(1, 500), record(2, 900))).uiState.value as ResultsUiState.Ready
        assertEquals(false, state.isNewBest)
    }

    @Test
    fun unknownResultIsMissing() = runTest {
        assertEquals(ResultsUiState.Missing, viewModel(7, emptyList()).uiState.value)
    }
}
