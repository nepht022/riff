package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A janela do PC perde o foco no meio da música (Alt+Tab): a partida pausa e solta as cores seguradas. */
class DesktopGameplayFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    // O evento de tecla avulso (sem o estado de teclas do performKeyInput): a subida do A nunca chega, como no PC.
    @OptIn(InternalComposeUiApi::class)
    @Test
    fun losingTheWindowFocusPausesAndReleasesTheFrets() {
        val session = playingSession(note(60_000))
        var pauses = 0
        val window = composeRule.showStage({ readyState(session) }, onPause = { pauses++ })
        composeRule.onRoot().performKeyPress(KeyEvent(Key.A, KeyEventType.KeyDown))
        assertTrue(session.isPressed(0))

        window.isWindowFocused = false
        composeRule.waitForIdle()

        assertEquals(1, pauses)
        assertFalse(session.isPressed(0))
        // A subida do A não chegou (foi em outra janela): de volta, apertar A de novo aperta, não é "repetição".
        window.isWindowFocused = true
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyPress(KeyEvent(Key.A, KeyEventType.KeyDown))
        assertTrue(session.isPressed(0))
        assertEquals(1, pauses)
    }
}
