package com.rodrigomachado.riff.feature.results

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.rodrigomachado.riff.core.data.ranking.SubmitStatus
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
import java.util.Locale

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

    private fun showRanking(status: SubmitStatus?, events: MutableList<String>) = composeRule.setContent {
        RiffTheme {
            ResultsContent(
                ResultsUiState.Ready(record(2, 48_300), "Música", "Banda", false, ranking = status, rankingOn = true),
                onPlayAgain = { _, _ -> },
                onDone = {},
                onSeeRanking = { id, title, difficulty -> events += "see:$id:$title:$difficulty" },
                onChooseName = { events += "name" },
            )
        }
    }

    @Test
    fun theResultShowsThePosition() {
        val events = mutableListOf<String>()
        showRanking(SubmitStatus.Placed(3, 27), events)

        composeRule.onNodeWithText("Ranking: 3º de 27").performScrollTo().assertExists()
        composeRule.onNodeWithText("Ver ranking").performScrollTo().performClick()

        assertEquals(listOf("see:a:Música:HARD"), events)
    }

    @Test
    fun withoutANameTheResultInvitesToChooseOne() {
        val events = mutableListOf<String>()
        showRanking(SubmitStatus.NoName, events)

        composeRule.onNodeWithText("Escolha um nome para entrar no ranking").performScrollTo().performClick()

        assertEquals(listOf("name"), events)
    }

    @Test
    fun queuedSaysItGoesWhenOnline() {
        showRanking(SubmitStatus.Queued, mutableListOf())

        composeRule.onNodeWithText("Sem internet: vai para o ranking quando voltar").performScrollTo().assertExists()
    }

    @Test
    fun notBestShowsOnlyTheWayToTheRanking() {
        showRanking(SubmitStatus.NotBest, mutableListOf())

        composeRule.onNodeWithText("Ranking:", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Ver ranking").performScrollTo().assertExists()
    }

    @Test
    fun withoutTheRankingNothingIsShown() {
        composeRule.setContent {
            RiffTheme {
                ResultsContent(ResultsUiState.Ready(record(2, 48_300), "Música", "Banda", false), { _, _ -> }, {})
            }
        }

        composeRule.onNodeWithText("Ver ranking").assertDoesNotExist()
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
                pane == "Resultado: 48.300 pontos, precisão de 88%. Novo recorde!"
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

    @Test
    fun oneSingularPointForTalkBack() {
        composeRule.setContent {
            RiffTheme {
                ResultsContent(
                    ResultsUiState.Ready(record(2, 1), "Música", "Banda", isNewBest = false),
                    onPlayAgain = { _, _ -> },
                    onDone = {},
                )
            }
        }

        composeRule.onNode(
            SemanticsMatcher("um ponto no singular") { node ->
                node.config.getOrNull(SemanticsProperties.PaneTitle) == "Resultado: 1 ponto, precisão de 88%."
            },
        ).assertExists()
    }

    /** Em inglês (o Locale padrão do processo, como o app define): o placar, o recorde e o apóstrofo simples. */
    @Test
    @Config(qualifiers = "en")
    fun inEnglishTheQueuedRankingReadsWithAPlainApostrophe() {
        val system = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            composeRule.setContent {
                RiffTheme {
                    ResultsContent(
                        ResultsUiState.Ready(
                            record(2, 48_300),
                            "Música",
                            "Banda",
                            isNewBest = true,
                            ranking = SubmitStatus.Queued,
                            rankingOn = true,
                        ),
                        onPlayAgain = { _, _ -> },
                        onDone = {},
                    )
                }
            }
            composeRule.waitForIdle()
        } finally {
            Locale.setDefault(system)
        }

        composeRule.onNodeWithText("48,300").assertExists()
        composeRule.onNodeWithText("NEW BEST").assertExists()
        composeRule.onNodeWithText("No internet: it goes to the ranking when you're back online")
            .performScrollTo().assertExists()
        composeRule.onNode(
            SemanticsMatcher("resumo em inglês") { node ->
                node.config.getOrNull(SemanticsProperties.PaneTitle) == "Result: 48,300 points, 88% accuracy. New best!"
            },
        ).assertExists()
    }
}
