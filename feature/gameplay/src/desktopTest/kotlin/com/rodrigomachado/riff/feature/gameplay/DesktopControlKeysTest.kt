package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.input.key.Key
import com.rodrigomachado.riff.core.model.ControlAction
import com.rodrigomachado.riff.core.model.Controls
import org.junit.Assert.assertEquals
import org.junit.Test

/** Os códigos de tecla padrão de `Controls` (sem Compose, em `:core:model`) são os do `Key.keyCode` do PC. */
class DesktopControlKeysTest {
    @Test
    fun theDefaultKeyCodesAreTheComposeKeys() {
        val expected = mapOf(
            ControlAction.FRET_1 to Key.A,
            ControlAction.FRET_2 to Key.S,
            ControlAction.FRET_3 to Key.J,
            ControlAction.FRET_4 to Key.K,
            ControlAction.FRET_5 to Key.L,
            ControlAction.STRUM_UP to Key.DirectionUp,
            ControlAction.STRUM_DOWN to Key.DirectionDown,
            ControlAction.PAUSE to Key.Enter,
        ).mapValues { it.value.keyCode }

        assertEquals(expected, Controls.DEFAULT_KEYS)
    }
}
