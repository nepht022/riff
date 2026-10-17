package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A partida da música nova com mapa só sugerido: abre pausada pedindo confirmação (spec Spotify contínuo §4). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class GameConfirmPanelTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    private fun showConfirm() {
        val session = GameSession(
            listOf(note(60_000)),
            FakeSongPlayer(),
            FakeSfxPlayer(),
            FakeTime(),
            audioOffsetMs = 0,
        )
        val state = GameplayUiState.Ready(
            "Título",
            "Banda",
            Difficulty.EXPERT,
            session,
            Settings.DEFAULT,
            isPaused = true,
            spotifyTrackUri = "spotify:track:x",
            awaitingConfirmation = true,
        )
        composeRule.setContent {
            RiffTheme {
                GameStage(
                    state,
                    reducedMotion = true,
                    onPause = {},
                    onResume = { events += "resume" },
                    onExit = { events += "exit" },
                    onSessionFinished = {},
                    onConfirm = { events += "confirm" },
                    onChooseOther = { events += "choose" },
                    animate = false,
                )
            }
        }
    }

    @Test
    fun theConfirmPanelOffersConfirmChooseAndBack() {
        showConfirm()

        composeRule.onNodeWithText("Mapa sugerido").assertExists()
        composeRule.onNodeWithText("Este mapa parece ser desta música: Título — Banda").assertExists()
        composeRule.onNodeWithText("Continuar").assertDoesNotExist()
        composeRule.onNodeWithText("Confirmar mapa e jogar").performClick()
        composeRule.onNodeWithText("Escolher outro").performClick()
        composeRule.onNodeWithText("Voltar ao menu").performClick()

        assertEquals(listOf("confirm", "choose", "exit"), events)
    }
}
