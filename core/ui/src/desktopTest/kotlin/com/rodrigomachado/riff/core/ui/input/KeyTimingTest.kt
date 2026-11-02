package com.rodrigomachado.riff.core.ui.input

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.Component
import java.awt.event.KeyEvent as AwtKeyEvent

/** O atraso da tecla no PC vem do instante do evento do AWT (relógio de parede), limitado a 0..200 ms. */
@OptIn(InternalComposeUiApi::class)
class KeyTimingTest {
    /** A tecla A descendo, com o evento do AWT do instante [whenMs]. */
    private fun keyAt(whenMs: Long): KeyEvent {
        val awt = AwtKeyEvent(object : Component() {}, AwtKeyEvent.KEY_PRESSED, whenMs, 0, AwtKeyEvent.VK_A, 'a')
        return KeyEvent(Key.A, KeyEventType.KeyDown, nativeEvent = awt)
    }

    /** O relógio de parede andou para trás depois da tecla: não é um atraso negativo. */
    @Test
    fun aKeyFromTheFutureIsNotLate() {
        assertEquals(0L, keyAt(System.currentTimeMillis() + 5_000).lateByMs())
    }

    /** Um ajuste do relógio no meio da medição não vira uma tecla apertada segundos atrás. */
    @Test
    fun aVeryLateKeyIsCappedAt200Ms() {
        assertEquals(200L, keyAt(System.currentTimeMillis() - 5_000).lateByMs())
    }

    @Test
    fun withoutAnAwtEventTheKeyIsOnTime() {
        assertEquals(0L, KeyEvent(Key.A, KeyEventType.KeyDown).lateByMs())
    }
}
