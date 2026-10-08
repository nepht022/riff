package com.rodrigomachado.riff.feature.results

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class ResultsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTheStatsAndPlaysAgainWithTheSameChartAndDifficulty() {
        var playAgain: Pair<String, Difficulty>? = null
        composeRule.setContent {
            RiffTheme {
                ResultsContent(
                    ResultsUiState.Ready(record(2, 48_300), "Música", "Banda", isNewBest = true),
                    onPlayAgain = { id, difficulty -> playAgain = id to difficulty },
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithText("48.300").assertExists()
        composeRule.onNodeWithText("NOVO RECORDE").assertExists()
        composeRule.onNodeWithText("88%").assertExists()
        composeRule.onNodeWithText("DIFÍCIL").assertExists()
        composeRule.onNodeWithText("Jogar de novo").performScrollTo().performClick()
        assertEquals("a" to Difficulty.HARD, playAgain)
    }

    @Test
    fun backReturnsToWhereTheGameStarted() {
        var done = false
        composeRule.setContent {
            RiffTheme {
                ResultsContent(
                    ResultsUiState.Ready(record(2, 48_300), "Música", "Banda", isNewBest = false),
                    onPlayAgain = { _, _ -> },
                    onDone = { done = true },
                )
            }
        }

        composeRule.onNodeWithText("Voltar").performScrollTo().performClick()

        assertTrue(done)
    }
}
