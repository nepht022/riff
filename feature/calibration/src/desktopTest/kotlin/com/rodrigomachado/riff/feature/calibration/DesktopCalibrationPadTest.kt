package com.rodrigomachado.riff.feature.calibration

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.createComposeRule
import com.rodrigomachado.riff.core.ui.input.Gamepad
import com.rodrigomachado.riff.core.ui.input.LocalGamepad
import com.rodrigomachado.riff.core.ui.input.LocalPadMenuSuspension
import com.rodrigomachado.riff.core.ui.input.PAD_MENU_BACK
import com.rodrigomachado.riff.core.ui.input.PadAction
import com.rodrigomachado.riff.core.ui.input.PadEvent
import com.rodrigomachado.riff.core.ui.input.PadMenuSuspension
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Um controle de mentira: o teste aperta os botões (o evento da partida e a descida crua, como o do PC). */
private class FakeGamepad : Gamepad {
    override val events = MutableSharedFlow<PadEvent>(extraBufferCapacity = 16)
    override val connected = MutableStateFlow(true)
    override val presses = MutableSharedFlow<String>(extraBufferCapacity = 16)
}

/**
 * No PC a calibração se mede também pelo controle: cada botão que desce é um toque, no instante em que foi lido (não
 * no de quando chegou à tela), e durante a medição o controle sai dos menus (só o ○ volta, e para a medição).
 */
class DesktopCalibrationPadTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val pad = FakeGamepad()
    private val suspension = PadMenuSuspension()
    private val taps = mutableListOf<Long>()

    /** Os botões que viraram teclas nos menus (o que o app do PC faz com cada descida que a suspensão deixa). */
    private val sent = mutableListOf<String>()

    private fun showMeasuring() {
        composeRule.setContent {
            CompositionLocalProvider(LocalGamepad provides pad, LocalPadMenuSuspension provides suspension) {
                LaunchedEffect(Unit) { pad.presses.collect { if (suspension.allows(it)) sent += it } }
                RiffTheme {
                    CalibrationContent(
                        state = CalibrationUiState.Measuring(taps = 0),
                        onStart = {},
                        onTap = { taps += it },
                        onNudge = {},
                        onSave = {},
                        onDone = {},
                        onReset = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** [button] desce, lido [agoMs] antes de agora: o evento da partida ([action]) e a descida crua. */
    private fun press(action: PadAction, button: String, agoMs: Long = 0): Long {
        val atNanos = System.nanoTime() - agoMs * NANOS_PER_MS
        composeRule.runOnIdle {
            assertTrue(pad.events.tryEmit(PadEvent(action, down = true, atNanos = atNanos)))
            assertTrue(pad.presses.tryEmit(button))
        }
        composeRule.waitForIdle()
        return atNanos / NANOS_PER_MS
    }

    @Test
    fun aPadButtonIsATapAtTheInstantItWasRead() {
        showMeasuring()

        val pressedAtMs = press(PadAction.FRET_4, CROSS, agoMs = 40)

        assertEquals(1, taps.size)
        assertTrue("toque em ${taps[0]}, apertado em $pressedAtMs", abs(taps[0] - pressedAtMs) <= TOLERANCE_MS)
        assertEquals("teclas mandadas aos menus", emptyList<String>(), sent)
    }

    @Test
    fun circleStillGoesBackToTheMenus() {
        showMeasuring()

        press(PadAction.FRET_2, PAD_MENU_BACK)

        assertEquals(listOf(PAD_MENU_BACK), sent)
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val TOLERANCE_MS = 5L
        const val CROSS = "m:0"
    }
}
