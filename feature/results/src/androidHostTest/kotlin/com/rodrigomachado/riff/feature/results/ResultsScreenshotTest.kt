package com.rodrigomachado.riff.feature.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.core.ui.theme.Spacing
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi")
class ResultsScreenshotTest {
    @Test
    fun newBest() {
        captureRoboImage("src/test/screenshots/Results_new_best.png") {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    ResultsContent(
                        ResultsUiState.Ready(
                            record(2, 48_300),
                            "Through the Fire and Flames",
                            "DragonForce",
                            isNewBest = true,
                            previousBest = 41_200,
                        ),
                        onPlayAgain = { _, _ -> },
                        onDone = {},
                    )
                }
            }
        }
    }

    /** A partida de sala: "Voltar à sala" e "Ver a sala" lado a lado, sem "Jogar de novo"; nota S, sem recorde. */
    @Test
    fun room() {
        captureRoboImage("src/test/screenshots/Results_room.png") {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    ResultsContent(
                        ResultsUiState.Ready(
                            record(2, 48_300).copy(accuracy = 0.985f),
                            "Bohemian Rhapsody",
                            "Queen",
                            isNewBest = false,
                        ),
                        onPlayAgain = { _, _ -> },
                        onDone = {},
                        inRoom = true,
                        onSeeRoom = {},
                    )
                }
            }
        }
    }

    /** Fonte do sistema em 200%: a tela rola, nada cortado no que aparece. */
    @Test
    @Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi", fontScale = 2f)
    fun withALargeFont() {
        captureRoboImage("src/test/screenshots/LargeFont_results.png") {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    ResultsContent(
                        ResultsUiState.Ready(
                            record(2, 48_300),
                            "Through the Fire and Flames",
                            "DragonForce",
                            isNewBest = true,
                            previousBest = 41_200,
                        ),
                        onPlayAgain = { _, _ -> },
                        onDone = {},
                    )
                }
            }
        }
    }

    /**
     * Fonte do sistema em 200%: os botões de baixo (fora da primeira tela da de cima), lado a lado; o rótulo pode
     * quebrar, mas não é cortado.
     */
    @Test
    @Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi", fontScale = 2f)
    fun buttonsWithALargeFont() {
        captureRoboImage("src/test/screenshots/LargeFont_results_buttons.png") {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        ResultsButtons(actions("Voltar", "Jogar de novo"))
                        ResultsButtons(actions("Voltar à sala", "Ver a sala"))
                    }
                }
            }
        }
    }

    private fun actions(back: String, main: String) =
        ResultsActions(ResultsButton(back) {}, ResultsButton(main) {}, onSeeRanking = {}, onChooseName = {})

    @Test
    fun rankingPlaced() = card(
        "Results_ranking_placed",
        com.rodrigomachado.riff.core.data.ranking.SubmitStatus.Placed(3, 27),
    )

    @Test
    fun rankingNoName() = card("Results_ranking_no_name", com.rodrigomachado.riff.core.data.ranking.SubmitStatus.NoName)

    private fun card(name: String, status: com.rodrigomachado.riff.core.data.ranking.SubmitStatus) {
        captureRoboImage("src/test/screenshots/$name.png") {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    ResultsContent(
                        ResultsUiState.Ready(
                            record(2, 48_300).copy(cleanStreakMs = 38_000),
                            "Through the Fire and Flames",
                            "DragonForce",
                            isNewBest = true,
                            ranking = status,
                            rankingOn = true,
                        ),
                        onPlayAgain = { _, _ -> },
                        onDone = {},
                    )
                }
            }
        }
    }
}
