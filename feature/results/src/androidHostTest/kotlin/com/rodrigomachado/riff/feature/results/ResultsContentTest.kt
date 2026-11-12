package com.rodrigomachado.riff.feature.results

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import com.rodrigomachado.riff.core.data.ranking.SubmitStatus
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.LocalMenuSound
import com.rodrigomachado.riff.core.ui.input.Gamepad
import com.rodrigomachado.riff.core.ui.input.LocalGamepad
import com.rodrigomachado.riff.core.ui.input.PadEvent
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
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
        // "artista · dificuldade" (antes, um selo em maiúsculas).
        composeRule.onNodeWithText("Difícil").assertExists()
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

    /** Na partida de sala, o Resultado só volta à sala: "Jogar de novo" abriria uma partida sozinho por cima dela. */
    @Test
    fun roomResultHasNoPlayAgain() {
        var done = false
        composeRule.setContent {
            RiffTheme {
                ResultsContent(
                    ResultsUiState.Ready(record(2, 48_300), "Música", "Banda", isNewBest = false),
                    onPlayAgain = { _, _ -> error("jogou de novo na sala") },
                    onDone = { done = true },
                    inRoom = true,
                )
            }
        }

        composeRule.onNodeWithText("Jogar de novo").assertDoesNotExist()
        composeRule.onNodeWithText("Voltar").assertDoesNotExist()
        composeRule.onNodeWithText("Voltar à sala").performScrollTo().performClick()

        assertTrue(done)
    }

    /** Na partida de sala, "Ver a sala" abre o Resultado da sala; "Voltar à sala" continua lá. */
    @Test
    fun roomResultSeesTheRoom() {
        var seen = 0
        composeRule.setContent {
            RiffTheme {
                ResultsContent(
                    ResultsUiState.Ready(record(2, 48_300), "Música", "Banda", isNewBest = false),
                    onPlayAgain = { _, _ -> error("jogou de novo na sala") },
                    onDone = {},
                    inRoom = true,
                    onSeeRoom = { seen++ },
                )
            }
        }

        composeRule.onNodeWithText("Voltar à sala").performScrollTo().assertExists()
        composeRule.onNodeWithText("Ver a sala").performScrollTo().performClick()

        assertEquals(1, seen)
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

    /** O carimbo da nota pela precisão (S ≥ 98%, A ≥ 90%, B ≥ 80%, C abaixo), lido como "Nota B". */
    @Test
    fun gradeStampFromAccuracy() {
        var accuracy by mutableStateOf(0.875f)
        composeRule.setContent {
            RiffTheme {
                ResultsContent(
                    ResultsUiState.Ready(record(2, 48_300).copy(accuracy = accuracy), "Música", "Banda", false),
                    onPlayAgain = { _, _ -> },
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithTag(ResultsTestTags.GRADE).assertContentDescriptionEquals("Nota B")
        listOf(0.98f to "S", 0.9f to "A", 0.79f to "C").forEach { (value, grade) ->
            accuracy = value
            composeRule.onNodeWithTag(ResultsTestTags.GRADE).assertContentDescriptionEquals("Nota $grade")
        }
    }

    /** "Novo recorde" com o recorde de antes ("antes: N"); a primeira partida (sem recorde anterior), sem o "antes". */
    @Test
    fun previousBestShownOnNewRecord() {
        var previous by mutableStateOf<Long?>(41_200)
        composeRule.setContent {
            RiffTheme {
                ResultsContent(
                    ResultsUiState.Ready(record(2, 48_300), "Música", "Banda", true, previousBest = previous),
                    onPlayAgain = { _, _ -> },
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithText("NOVO RECORDE").assertExists()
        composeRule.onNodeWithText("antes: 41.200").assertExists()
        previous = null
        composeRule.onNodeWithText("NOVO RECORDE").assertExists()
        composeRule.onNodeWithText("antes:", substring = true).assertDoesNotExist()
    }

    /** "Voltar" e "Jogar de novo" lado a lado, na mesma linha (Voltar à esquerda). */
    @Test
    fun buttonsSideBySide() {
        composeRule.setContent {
            RiffTheme {
                ResultsContent(
                    ResultsUiState.Ready(record(2, 48_300), "Música", "Banda", false),
                    onPlayAgain = { _, _ -> },
                    onDone = {},
                )
            }
        }

        val back = composeRule.onNodeWithText("Voltar").performScrollTo().fetchSemanticsNode().boundsInRoot
        val again = composeRule.onNodeWithText("Jogar de novo").fetchSemanticsNode().boundsInRoot
        assertTrue("os dois na mesma linha: $back / $again", back.bottom > again.top && again.bottom > back.top)
        assertTrue("Voltar à esquerda: $back / $again", back.right <= again.left)
    }

    /** No celular (360 dp), os rótulos dos dois botões lado a lado cabem numa linha cada. */
    @Test
    @Config(qualifiers = "pt-rBR-w360dp-h780dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // a medida do texto de verdade
    fun buttonLabelsFitOneLineOnThePhone() {
        composeRule.setContent {
            RiffTheme {
                ResultsContent(
                    ResultsUiState.Ready(record(2, 48_300), "Música", "Banda", false),
                    onPlayAgain = { _, _ -> },
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithText("Jogar de novo").performScrollTo()
        assertEquals(1, lineCount("Jogar de novo"))
        assertEquals(1, lineCount("Voltar"))
    }

    /** As linhas que o texto [text] ocupou na tela. */
    private fun lineCount(text: String): Int {
        val node = composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().lineCount
    }

    /** Na partida de sala: "Voltar à sala" e "Ver a sala", lado a lado, sem "Jogar de novo". */
    @Test
    fun roomResultKeepsSeeRoomAndBackToRoom() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            RiffTheme {
                ResultsContent(
                    ResultsUiState.Ready(record(2, 48_300), "Música", "Banda", isNewBest = false),
                    onPlayAgain = { _, _ -> error("jogou de novo na sala") },
                    onDone = { events += "back" },
                    inRoom = true,
                    onSeeRoom = { events += "room" },
                )
            }
        }

        composeRule.onNodeWithText("Jogar de novo").assertDoesNotExist()
        val back = composeRule.onNodeWithText("Voltar à sala").performScrollTo().fetchSemanticsNode().boundsInRoot
        val see = composeRule.onNodeWithText("Ver a sala").fetchSemanticsNode().boundsInRoot
        assertTrue("os dois na mesma linha: $back / $see", back.bottom > see.top && see.bottom > back.top)
        composeRule.onNodeWithText("Ver a sala").performClick()
        composeRule.onNodeWithText("Voltar à sala").performClick()

        assertEquals(listOf("room", "back"), events)
    }

    /** No PC (com o controle do PC no `LocalGamepad`), a barra de teclas no rodapé; no celular, nenhuma. */
    @Test
    @Config(qualifiers = "pt-rBR-w1024dp-h768dp")
    fun pcShowsKeyHints() {
        composeRule.setContent {
            CompositionLocalProvider(LocalGamepad provides PadOn) {
                RiffTheme {
                    ResultsContent(
                        ResultsUiState.Ready(record(2, 48_300), "Música", "Banda", isNewBest = false),
                        onPlayAgain = { _, _ -> },
                        onDone = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(ResultsTestTags.KEY_HINTS).assertExists()
        composeRule.onNodeWithText("Mover").assertExists()
        composeRule.onNodeWithText("✕").assertExists()
    }

    @Test
    fun thePhoneHasNoKeyHints() {
        composeRule.setContent {
            RiffTheme {
                ResultsContent(ResultsUiState.Ready(record(2, 48_300), "Música", "Banda", false), { _, _ -> }, {})
            }
        }

        composeRule.onNodeWithTag(ResultsTestTags.KEY_HINTS).assertDoesNotExist()
    }

    private object PadOn : Gamepad {
        override val events = emptyFlow<PadEvent>()
        override val connected = MutableStateFlow(true)
        override val presses = emptyFlow<String>()
    }
}
