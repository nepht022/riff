package com.rodrigomachado.riff.feature.ranking

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.data.ranking.Board
import com.rodrigomachado.riff.core.data.ranking.BoardLine
import com.rodrigomachado.riff.core.data.ranking.RankedSong
import com.rodrigomachado.riff.core.data.ranking.RankingMetric
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class RankingScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val ranking = FakeRanking()
    private val dispatcher = StandardTestDispatcher()
    private val board = Board(
        listOf(Difficulty.HARD, Difficulty.EXPERT),
        Difficulty.HARD,
        27,
        listOf(
            BoardLine(1, "Ana", 61_200.0, you = false),
            BoardLine(3, "Rodrigo", 48_300.0, you = true),
        ),
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun boardViewModel(difficulty: String? = "HARD") = RankingBoardViewModel(
        SavedStateHandle(mapOf("chartId" to "m1", "title" to "Música", "difficulty" to difficulty)),
        ranking,
    )

    @Test
    fun searchingSongsAsksTheServerWithTheText() = runTest(dispatcher) {
        val vm = RankingViewModel(ranking)
        advanceUntilIdle()

        vm.onQuery("to")
        vm.onQuery("tool") // a busca espera parar de digitar
        advanceTimeBy(301)

        assertEquals(listOf("", "tool"), ranking.songQueries)
    }

    @Test
    fun boardStartsOnTheDifficultyFromTheRoute() = runTest(dispatcher) {
        boardViewModel()
        advanceUntilIdle()

        assertEquals("m1 HARD pontos desc ''", ranking.boardQueries.single())
    }

    @Test
    fun metricDirectionAndDifficultyChangeTheBoard() = runTest(dispatcher) {
        val vm = boardViewModel()
        advanceUntilIdle()

        vm.onMetric(RankingMetric.COMBO)
        advanceUntilIdle()
        vm.onToggleDirection()
        advanceUntilIdle()
        vm.onDifficulty(Difficulty.EXPERT)
        advanceUntilIdle()

        assertEquals(
            listOf("m1 HARD combo desc ''", "m1 HARD combo asc ''", "m1 EXPERT combo asc ''"),
            ranking.boardQueries.drop(1),
        )
    }

    @Test
    fun songsListShowsPlayersAndYourPlace() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            RiffTheme {
                RankingSongsContent(
                    RankingSongsUiState(songs = listOf(RankedSong("m1", "Through the Fire", "DragonForce", 27, 3))),
                    onQuery = {},
                    onSong = { events += it.chartId },
                    onRetry = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("DragonForce · 27 jogadores · você 3º").assertExists()
        composeRule.onNodeWithText("Through the Fire").performClick()
        assertEquals(listOf("m1"), events)
    }

    @Test
    fun boardMarksYouAndShowsTheValues() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            RiffTheme {
                RankingBoardContent(
                    RankingBoardUiState(title = "Música", board = board, difficulty = Difficulty.HARD),
                    actions = BoardActions(onToggleDirection = { events += "sentido" }),
                )
            }
        }

        composeRule.onNodeWithText("Rodrigo (você)").assertExists()
        composeRule.onNodeWithText("48.300").assertExists()
        composeRule.onNodeWithText("3º").assertExists()
        composeRule.onNodeWithText("↓ maior").performClick()
        assertEquals(listOf("sentido"), events)
    }

    @Test
    fun valuesFollowTheMetric() {
        assertEquals("88%", formatValue(RankingMetric.ACCURACY, 0.876))
        assertEquals("0:38", formatValue(RankingMetric.CLEAN, 38_500.0))
        assertEquals("42", formatValue(RankingMetric.COMBO, 42.0))
    }

    @Test
    fun offlineShowsTryAgain() {
        var retried = false
        composeRule.setContent {
            RiffTheme {
                RankingSongsContent(RankingSongsUiState(offline = true), {}, {}, { retried = true }, {})
            }
        }

        composeRule.onNodeWithText("Sem conexão com o ranking").assertExists()
        composeRule.onNodeWithText("Tentar de novo").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun notConfiguredExplains() = runTest(dispatcher) {
        ranking.isConfigured = false
        val vm = RankingViewModel(ranking)
        advanceUntilIdle()

        assertEquals(true, vm.state.value.notConfigured)
        assertEquals(emptyList<String>(), ranking.songQueries)
    }
}
