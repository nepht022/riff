package com.rodrigomachado.riff.feature.room

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.rodrigomachado.riff.core.room.BoardLine
import com.rodrigomachado.riff.core.room.RoomDevice
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A tela do Resultado da sala: a classificação, "saiu", "sem conexão", o aviso de espera e o Voltar à sala. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class RoomResultsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: RoomResultsUiState, onBack: () -> Unit = {}) = composeRule.setContent {
        RiffTheme { RoomResultsContent(state, onBackToRoom = onBack) }
    }

    @Test
    fun showsEachLineWithPointsAccuracyAndCombo() {
        show(sampleResults(4))

        composeRule.onNodeWithText("Resultado da sala").assertExists()
        composeRule.onAllNodesWithText("Ana")[0].assertExists()
        composeRule.onNodeWithText("Precisão 96%", substring = true).performScrollTo().assertExists()
        composeRule.onNodeWithText("Maior combo 212", substring = true).performScrollTo().assertExists()
        composeRule.onAllNodesWithText("98.420", substring = true)[0].assertExists()
    }

    @Test
    fun leftAndNoConnectionAreShown() {
        show(
            RoomResultsUiState.Ready(
                lines = listOf(
                    line("u1", "Rodrigo", 5_000, finished = true),
                    line("bia", "Bia", 3_000, stale = true),
                    line("ana", "Ana", 9_000, left = true),
                ),
                me = "u1",
                round = 1,
            ),
        )

        composeRule.onNodeWithText("saiu").performScrollTo().assertExists()
        composeRule.onNodeWithText("sem conexão").performScrollTo().assertExists()
    }

    @Test
    fun saysItWaitsWhileSomeoneStillPlays() {
        show(
            RoomResultsUiState.Ready(
                lines = listOf(line("u1", "Rodrigo", 5_000, finished = true), line("bia", "Bia", 3_000)),
                me = "u1",
                round = 1,
            ),
        )

        composeRule.onNodeWithText("Esperando os outros terminarem…").assertExists()
        composeRule.onNodeWithText("jogando").performScrollTo().assertExists()
    }

    @Test
    fun backToTheRoomFromRoomResults() {
        var back = 0
        show(sampleResults(2)) { back++ }

        composeRule.onNodeWithText("Voltar à sala").performScrollTo().performClick()

        assertEquals(1, back)
    }

    /** Ainda sem a sala (a presença lendo): o Voltar à sala já está lá. */
    @Test
    fun loadingStillGoesBackToTheRoom() {
        var back = 0
        show(RoomResultsUiState.Loading) { back++ }

        composeRule.onNodeWithText("Voltar à sala").performClick()

        assertEquals(1, back)
    }
}

/** Um Resultado da sala com [count] jogadores (até 4), todos terminaram; o próprio é o Rodrigo. */
internal fun sampleResults(count: Int): RoomResultsUiState.Ready = RoomResultsUiState.Ready(
    lines = listOf(
        line("ana", "Ana", 98_420, accuracy = 0.96f, maxCombo = 212, finished = true),
        line("u1", "Rodrigo", 87_150, accuracy = 0.91f, maxCombo = 140, finished = true),
        line("bruno", "Bruno_88", 61_300, accuracy = 0.78f, maxCombo = 66, finished = true, device = RoomDevice.PC),
        line("carla", "Carla", 40_020, accuracy = 0.62f, maxCombo = 31, finished = true),
    ).take(count),
    me = "u1",
    round = 3,
)

@Suppress("LongParameterList") // uma linha do placar inteira, com os padrões de quem está jogando
internal fun line(
    uid: String,
    name: String,
    score: Long,
    accuracy: Float = 0.8f,
    maxCombo: Int = 20,
    finished: Boolean = false,
    left: Boolean = false,
    stale: Boolean = false,
    device: RoomDevice = RoomDevice.PHONE,
) = BoardLine(uid, name, device, score, combo = 0, maxCombo, accuracy, finished, left, stale)
