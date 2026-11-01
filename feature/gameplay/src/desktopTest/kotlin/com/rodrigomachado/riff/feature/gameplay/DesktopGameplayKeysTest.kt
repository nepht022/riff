package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.rodrigomachado.riff.core.model.Judgment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** O teclado do PC chega à partida: as cores com a palheta acertam, Esc pausa e, na pausa, Enter continua. */
class DesktopGameplayKeysTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun holdingAAndStrummingDownHitsTheNote() {
        val session = playingSession(note(1_000, lane = 0), positionMs = 1_000)
        composeRule.showStage({ readyState(session) })

        composeRule.onRoot().performKeyInput {
            keyDown(Key.A)
            pressKey(Key.DirectionDown)
        }

        assertEquals(Judgment.PERFECT, session.lastJudgment)
        assertEquals(1, session.engine.combo)
        assertTrue(session.isPressed(0))
    }

    @Test
    fun escapePausesAndEnterResumes() {
        val session = playingSession(note(60_000))
        var paused by mutableStateOf(false)
        composeRule.showStage({ readyState(session, isPaused = paused) }, onPause = { paused = true }) {
            paused = false
        }

        composeRule.onRoot().performKeyInput { pressKey(Key.Escape) }
        assertTrue(paused)
        composeRule.onNodeWithTag(GameplayTestTags.EXIT).assertExists()

        composeRule.onRoot().performKeyInput { pressKey(Key.Enter) }
        assertFalse(paused)
        composeRule.onNodeWithTag(GameplayTestTags.EXIT).assertDoesNotExist()
    }
}
