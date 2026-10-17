package com.rodrigomachado.riff.feature.results

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.LocalMenuSound
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
    fun theCleanTimeIsShown() {
        composeRule.setContent {
            RiffTheme {
                ResultsContent(
                    ResultsUiState.Ready(record(2, 48_300).copy(cleanStreakMs = 38_000), "Música", "Banda", false),
                    onPlayAgain = { _, _ -> },
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithText("Tempo sem errar").assertExists()
        composeRule.onNodeWithText("0:38").assertExists()
    }

    /** Partida de antes do ranking: sem a medida, a linha não aparece (nada de "0:00" falso). */
    @Test
    fun anOldMatchHasNoCleanTime() {
        composeRule.setContent {
            RiffTheme {
                ResultsContent(
                    ResultsUiState.Ready(record(2, 48_300), "Música", "Banda", false),
                    onPlayAgain = { _, _ -> },
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithText("Tempo sem errar").assertDoesNotExist()
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

    @Test
    fun talkBackAnnouncesTheResultWhenTheScreenAppears() {
        composeRule.setContent {
            RiffTheme {
                ResultsContent(
                    ResultsUiState.Ready(record(2, 48_300), "Música", "Banda", isNewBest = true),
                    onPlayAgain = { _, _ -> },
                    onDone = {},
                )
            }
        }

        composeRule.onNode(
            SemanticsMatcher("resumo do resultado como título do painel") { node ->
                val pane = node.config.getOrNull(SemanticsProperties.PaneTitle).orEmpty()
                "48.300" in pane && "88%" in pane && "NOVO RECORDE" in pane
            },
        ).assertExists()
    }

    @Test
    fun playingAgainConfirms() {
        val sounds = mutableListOf<MenuSound>()
        composeRule.setContent {
            CompositionLocalProvider(LocalMenuSound provides { sounds += it }) {
                RiffTheme {
                    ResultsContent(
                        ResultsUiState.Ready(record(2, 48_300), "Música", "Banda", isNewBest = false),
                        onPlayAgain = { _, _ -> },
                        onDone = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Jogar de novo").performScrollTo().performClick()

        // O Voltar soa pela navegação (a tela sai da pilha), não pelo botão: assim o gesto também soa, uma vez só.
        assertEquals(listOf(MenuSound.CONFIRM), sounds)
    }
}
