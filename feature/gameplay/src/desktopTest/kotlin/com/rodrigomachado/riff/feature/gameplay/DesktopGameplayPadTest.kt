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
import com.rodrigomachado.riff.core.ui.input.Gamepad
import com.rodrigomachado.riff.core.ui.input.PadAction
import com.rodrigomachado.riff.core.ui.input.PadEvent
import com.rodrigomachado.riff.core.ui.input.PadMenuSuspension
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Um controle de mentira: o teste aperta os botões e liga/desliga o cabo. */
private class FakeGamepad : Gamepad {
    override val events = MutableSharedFlow<PadEvent>(extraBufferCapacity = 16)
    override val connected = MutableStateFlow(true)
    override val presses = MutableSharedFlow<String>(extraBufferCapacity = 16)
}

/** O controle do PC chega à partida como o teclado: cores e palheta acertam, Start pausa e continua. */
class DesktopGameplayPadTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val pad = FakeGamepad()

    /** [action] descendo (ou subindo), lida [agoMs] antes de agora; na thread da janela, como a coleta. */
    private fun press(action: PadAction, down: Boolean = true, agoMs: Long = 0) {
        composeRule.runOnIdle {
            assertTrue(pad.events.tryEmit(PadEvent(action, down, System.nanoTime() - agoMs * 1_000_000)))
        }
        composeRule.waitForIdle()
    }

    @Test
    fun holdingTriangleAndStrummingHitsTheGreenNote() {
        val session = playingSession(note(1_000, lane = 0), positionMs = 1_000)
        composeRule.showStage({ readyState(session) }, gamepad = pad)

        press(PadAction.FRET_1)
        press(PadAction.STRUM)

        assertEquals(Judgment.PERFECT, session.lastJudgment)
        assertEquals(1, session.engine.combo)
        assertTrue(session.isPressed(0))
        press(PadAction.FRET_1, down = false)
        assertFalse(session.isPressed(0))
    }

    /** A palhetada esperou 30 ms na fila: julgada no instante dela (perfeito), não no de agora (80 ms, bom). */
    @Test
    fun anEventThatWaitedIsJudgedAtItsOwnTime() {
        val session = playingSession(note(1_000, lane = 0), positionMs = 1_080)
        composeRule.showStage({ readyState(session) }, gamepad = pad)

        press(PadAction.FRET_1)
        press(PadAction.STRUM, agoMs = 30)

        assertEquals(Judgment.PERFECT, session.lastJudgment)
    }

    @Test
    fun startPausesAndStartAgainResumes() {
        val session = playingSession(note(60_000))
        var paused by mutableStateOf(false)
        composeRule.showStage({ readyState(session, isPaused = paused) }, { paused = true }, pad) { paused = false }

        press(PadAction.PAUSE)
        press(PadAction.PAUSE, down = false)
        assertTrue(paused)
        composeRule.onNodeWithTag(GameplayTestTags.EXIT).assertExists()

        press(PadAction.PAUSE)
        assertFalse(paused)
        composeRule.onNodeWithTag(GameplayTestTags.EXIT).assertDoesNotExist()
    }

    /**
     * Start continua e, no mesmo quadro (antes de a tela recompor sem a pausa), o ✕ desce: ele é uma cor, não um
     * Enter nos menus (que, chegando depois, pausaria de novo).
     */
    @Test
    fun aCrossRightAfterResumingIsNotAMenuKey() {
        val session = playingSession(note(60_000))
        var paused by mutableStateOf(true)
        var pauses = 0
        val suspension = PadMenuSuspension()
        composeRule.showStage(
            { readyState(session, isPaused = paused) },
            {
                pauses++
                paused = true
            },
            pad,
            onResume = { paused = false },
            suspension = suspension,
        )
        composeRule.mainClock.autoAdvance = false

        press(PadAction.PAUSE)
        press(PadAction.FRET_4)
        // A descida crua do ✕ chega à coleta dos menus logo depois do evento da partida, como no PC.
        val sent = composeRule.runOnIdle { suspension.allows(CROSS) }
        composeRule.onNodeWithTag(GameplayTestTags.EXIT).assertExists() // o mesmo quadro: a pausa ainda na tela
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        // A tecla falsa chegaria à janela depois (`invokeLater`), já sem a pausa na tela.
        if (sent) composeRule.runOnUiThread { composeRule.onRoot().performKeyInput { pressKey(Key.Enter) } }
        composeRule.waitForIdle()

        assertFalse("o ✕ virou um Enter nos menus", sent)
        assertFalse("pausou de novo", paused)
        assertEquals(0, pauses)
    }

    /**
     * Start continua e, no mesmo quadro, a seta do teclado palheta (a nota acerta): não é engolida pela pausa que já
     * fechou. Continuar pelo teclado não tem esse intervalo (o Compose recompõe antes de cada tecla).
     */
    @Test
    fun anArrowRightAfterStartResumesStrums() {
        val session = playingSession(note(1_000, lane = 0), positionMs = 1_000).also { it.pause() }
        var paused by mutableStateOf(true)
        composeRule.showStage({ readyState(session, isPaused = paused) }, { paused = true }, pad) {
            session.resume()
            paused = false
        }
        composeRule.mainClock.autoAdvance = false
        press(PadAction.PAUSE)
        composeRule.onNodeWithTag(GameplayTestTags.EXIT).assertExists() // o mesmo quadro: a pausa ainda na tela
        composeRule.runOnUiThread {
            composeRule.onRoot().performKeyInput {
                keyDown(Key.A)
                pressKey(Key.DirectionDown)
            }
        }
        composeRule.runOnIdle { assertEquals(Judgment.PERFECT, session.lastJudgment) }
    }

    /** Alt+Tab com o controle na mão: o GLFW lê o controle em segundo plano, mas a partida não ouve. */
    @Test
    fun withTheWindowUnfocusedThePadIsIgnored() {
        val session = playingSession(note(1_000, lane = 0), positionMs = 1_000)
        var pauses = 0
        var resumes = 0
        val window = composeRule.showStage({ readyState(session) }, { pauses++ }, pad) { resumes++ }
        window.isWindowFocused = false
        composeRule.waitForIdle()
        val pausesByFocus = pauses

        press(PadAction.FRET_1)
        press(PadAction.STRUM)
        press(PadAction.PAUSE)

        assertFalse(session.isPressed(0))
        assertNull(session.lastJudgment)
        assertEquals(pausesByFocus, pauses)
        assertEquals(0, resumes)
    }

    /** O cabo saiu no meio da música: pausa (uma vez), e a cor que sobe depois não acerta nada. */
    @Test
    fun unpluggingThePadPausesTheMatch() {
        val session = playingSession(note(60_000))
        var pauses = 0
        composeRule.showStage({ readyState(session) }, { pauses++ }, pad)
        press(PadAction.FRET_1)

        composeRule.runOnIdle { pad.connected.value = false }
        press(PadAction.FRET_1, down = false)

        assertEquals(1, pauses)
        assertFalse(session.isPressed(0))
    }

    private companion object {
        /** O ✕ (o A do layout de controle): Enter nos menus. */
        const val CROSS = "m:0"
    }
}
