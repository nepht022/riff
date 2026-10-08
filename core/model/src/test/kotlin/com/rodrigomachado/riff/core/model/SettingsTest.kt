package com.rodrigomachado.riff.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class SettingsTest {
    @Test
    fun defaultSfxVolumeIsSixtyPercent() {
        assertEquals(0.6f, Settings.DEFAULT.sfxVolume)
    }

    @Test
    fun acceptsSfxVolumeFromZeroToOne() {
        assertDoesNotThrow { Settings.DEFAULT.copy(sfxVolume = 0f) }
        assertDoesNotThrow { Settings.DEFAULT.copy(sfxVolume = 1f) }
    }

    @Test
    fun rejectsSfxVolumeOutsideZeroToOne() {
        assertThrows<IllegalArgumentException> { Settings.DEFAULT.copy(sfxVolume = -0.1f) }
        assertThrows<IllegalArgumentException> { Settings.DEFAULT.copy(sfxVolume = 1.1f) }
        assertThrows<IllegalArgumentException> { Settings.DEFAULT.copy(sfxVolume = Float.NaN) }
    }
}
