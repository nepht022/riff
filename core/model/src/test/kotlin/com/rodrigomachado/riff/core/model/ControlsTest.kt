package com.rodrigomachado.riff.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ControlsTest {
    @Test
    fun theDefaultsAreTodaysKeys() {
        val controls = Controls()

        assertEquals(true, controls.strum)
        assertEquals(
            listOf(65, 83, 74, 75, 76, 38, 40, 10).map(Controls::desktopKey), // A S J K L ↑ ↓ Enter
            ControlAction.entries.map { controls.keys[it] },
        )
        assertEquals(
            listOf("m:3", "m:1", "m:2", "m:0", "m:5", "m:11", "m:13", "m:7"),
            ControlAction.entries.map { controls.pad[it] },
        )
    }

    /** O `Key.A.keyCode` do Compose no PC (conferido em `DesktopControlKeysTest`, que tem o Compose). */
    @Test
    fun aDesktopKeyCarriesTheStandardLocation() {
        assertEquals(4_294_967_361L, Controls.desktopKey(65))
    }

    @Test
    fun choosingAKeyInUseSwapsTheTwoActions() {
        val controls = Controls().withKey(ControlAction.FRET_1, Controls.desktopKey(83)) // S, que era do FRET_2

        assertEquals(Controls.desktopKey(83), controls.keys[ControlAction.FRET_1])
        assertEquals(Controls.desktopKey(65), controls.keys[ControlAction.FRET_2])
        assertEquals(ControlAction.entries.size, controls.keys.values.toSet().size)
    }

    @Test
    fun choosingAButtonInUseSwapsTheTwoActions() {
        val controls = Controls().withPad(ControlAction.PAUSE, "m:3") // △, que era do FRET_1

        assertEquals("m:3", controls.pad[ControlAction.PAUSE])
        assertEquals("m:7", controls.pad[ControlAction.FRET_1])
        assertEquals(ControlAction.entries.size, controls.pad.values.toSet().size)
    }

    @Test
    fun choosingAFreeKeyChangesOnlyThatAction() {
        val q = Controls.desktopKey(81)
        val controls = Controls().withKey(ControlAction.FRET_1, q)

        assertEquals(Controls.DEFAULT_KEYS + (ControlAction.FRET_1 to q), controls.keys)
    }
}
