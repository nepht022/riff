package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Test

/** As teclas da partida no PC, sem a repetição automática do teclado. */
class KeyboardInputTest {
    private val events = mutableListOf<String>()
    private val input = KeyboardInput(
        onFretDown = { events += "↓cor $it" },
        onFretUp = { events += "↑cor $it" },
        onStrum = { events += "palheta" },
        onPause = { events += "pausa" },
    )

    @Test
    fun theFretsAreASJKL() {
        listOf(Key.A, Key.S, Key.J, Key.K, Key.L).forEach { input.keyDown(it) }

        assertEquals((0..4).map { "↓cor $it" }, events)
    }

    @Test
    fun holdingAKeyDoesNotRepeat() {
        input.keyDown(Key.A)
        input.keyDown(Key.A) // repetição automática
        input.keyDown(Key.DirectionDown)
        input.keyDown(Key.DirectionDown)
        input.keyUp(Key.A)

        assertEquals(listOf("↓cor 0", "palheta", "↑cor 0"), events)
    }

    @Test
    fun upAndDownBothStrum() {
        input.keyDown(Key.DirectionUp)
        input.keyUp(Key.DirectionUp)
        input.keyDown(Key.DirectionDown)

        assertEquals(listOf("palheta", "palheta"), events)
    }

    @Test
    fun enterAndEscapePause() {
        input.keyDown(Key.Enter)
        input.keyDown(Key.Escape)

        assertEquals(listOf("pausa", "pausa"), events)
    }

    @Test
    fun otherKeysAreIgnored() {
        assertEquals(false, input.keyDown(Key.Q))
        assertEquals(emptyList<String>(), events)
    }
}
