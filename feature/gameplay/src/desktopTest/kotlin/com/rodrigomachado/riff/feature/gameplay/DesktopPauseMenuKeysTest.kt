package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A pausa e a tela "Não dá para jogar" do PC só com o teclado: na pausa as setas andam pelos botões dela (a primeira
 * foca o Continuar) e Enter aciona o focado; sem foco, Enter continua. Tocando, as setas seguem palhetando. As
 * teclas chegam na thread da janela.
 */
class DesktopPauseMenuKeysTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun arrowsMoveBetweenThePauseButtons() {
        composeRule.showStage({ readyState(playingSession(note(60_000)), isPaused = true) })

        press(Key.DirectionDown)
        assertTrue("a primeira seta não focou o Continuar", focused(RESUME))

        press(Key.DirectionDown)
        assertFalse("a segunda seta não saiu do Continuar", focused(RESUME))
        assertTrue("a segunda seta não focou outro botão da pausa", focused(IN_PAUSE_MENU))
    }

    @Test
    fun enterOnExitLeavesTheMatch() {
        var exits = 0
        var resumes = 0
        composeRule.showStage(
            { readyState(playingSession(note(60_000)), isPaused = true) },
            onResume = { resumes++ },
            onExit = { exits++ },
        )

        pressUntilFocused(Key.DirectionDown, hasTestTag(GameplayTestTags.EXIT))
        press(Key.Enter)

        assertEquals(1, exits)
        assertEquals(0, resumes)
    }

    @Test
    fun enterWithNothingFocusedResumes() {
        var paused by mutableStateOf(true)
        composeRule.showStage({ readyState(playingSession(note(60_000)), isPaused = paused) }) { paused = false }

        press(Key.Enter)

        assertFalse(paused)
    }

    @Test
    fun escapeOnTheUnavailableScreenGoesBack() {
        var exits = 0
        composeRule.setContent {
            RiffTheme { UnavailableScreen(UnavailableReason.NO_AUDIO, onExit = { exits++ }) }
        }
        composeRule.waitForIdle()

        press(Key.Escape)

        assertEquals(1, exits)
    }

    @Test
    fun arrowsStillStrumWhilePlaying() {
        val session = playingSession(note(1_000, lane = 0), positionMs = 1_000)
        composeRule.showStage({ readyState(session) })

        composeRule.runOnUiThread {
            composeRule.onRoot().performKeyInput {
                keyDown(Key.A)
                pressKey(Key.DirectionDown)
            }
        }
        composeRule.waitForIdle()

        assertEquals(Judgment.PERFECT, session.lastJudgment)
        assertFalse("a seta tocando focou um botão", focused(IN_PAUSE_MENU))
    }

    private fun press(key: Key) {
        composeRule.runOnUiThread { composeRule.onRoot().performKeyInput { pressKey(key) } }
        composeRule.waitForIdle()
    }

    private fun pressUntilFocused(key: Key, matcher: SemanticsMatcher) {
        repeat(MAX_PRESSES) {
            if (focused(matcher)) return
            press(key)
        }
        assertTrue("$matcher não ficou com o foco", focused(matcher))
    }

    private fun focused(matcher: SemanticsMatcher) =
        composeRule.onAllNodes(matcher and isFocused()).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val MAX_PRESSES = 8
        val IN_PAUSE_MENU = hasAnyAncestor(hasTestTag(GameplayTestTags.PAUSE_MENU))

        // O foco fica no botão clicável, dentro do contêiner com a etiqueta.
        val RESUME = hasAnyAncestor(hasTestTag(GameplayTestTags.RESUME))
    }
}
