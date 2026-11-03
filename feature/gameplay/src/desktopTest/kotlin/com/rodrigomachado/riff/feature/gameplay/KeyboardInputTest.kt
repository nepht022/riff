package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.input.key.Key
import com.rodrigomachado.riff.core.model.ControlAction
import com.rodrigomachado.riff.core.model.Controls
import org.junit.Assert.assertEquals
import org.junit.Test

/** As teclas da partida no PC, sem a repetição automática do teclado. */
class KeyboardInputTest {
    private val events = mutableListOf<String>()
    private val input = input(Controls.DEFAULT_KEYS)

    /** As teclas [keys] (o `Key.keyCode` do PC, como gravado nas preferências). */
    private fun input(keys: Map<ControlAction, Long>) = KeyboardInput(
        keys = keys,
        onFretDown = { events += "↓cor $it" },
        onFretUp = { events += "↑cor $it" },
        onStrum = { events += "palheta" },
        onPause = { events += "pausa" },
    )

    private fun choosing(action: ControlAction, key: Key) = input(Controls().withKey(action, key.keyCode).keys)

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

    @Test
    fun aChosenKeyPlaysItsFret() {
        val chosen = choosing(ControlAction.FRET_1, Key.Q)

        assertEquals(true, chosen.keyDown(Key.Q))
        assertEquals(false, chosen.keyDown(Key.A))
        assertEquals(listOf("↓cor 0"), events)
    }

    /** O Esc pausa mesmo com outra tecla escolhida para a pausa (e o Enter de antes deixa de pausar). */
    @Test
    fun escapeAlwaysPauses() {
        val chosen = choosing(ControlAction.PAUSE, Key.P)

        chosen.keyDown(Key.Escape)
        chosen.keyDown(Key.P)
        assertEquals(false, chosen.keyDown(Key.Enter))
        assertEquals(listOf("pausa", "pausa"), events)
    }

    /** O Enter (também da navegação do app) escolhido para o amarelo: na partida vale a escolha, não a pausa. */
    @Test
    fun enterChosenAsAFretIsAFret() {
        val chosen = choosing(ControlAction.FRET_3, Key.Enter)

        chosen.keyDown(Key.Enter)
        chosen.keyUp(Key.Enter)
        assertEquals(listOf("↓cor 2", "↑cor 2"), events)
    }
}
