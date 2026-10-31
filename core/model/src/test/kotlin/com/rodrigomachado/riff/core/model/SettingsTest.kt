package com.rodrigomachado.riff.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class SettingsTest {
    @Test
    fun defaultsAreFullAppVolumeAndSixtyPercentEffects() {
        assertEquals(1f, Settings.DEFAULT.masterVolume)
        assertEquals(0.6f, Settings.DEFAULT.hitVolume)
        assertEquals(0.6f, Settings.DEFAULT.missVolume)
        assertEquals(1f, Settings.DEFAULT.noteSpeed)
    }

    @Test
    fun volumesGoFromSilentToFull() {
        assertDoesNotThrow { Settings.DEFAULT.copy(masterVolume = 0f, hitVolume = 0f, missVolume = 0f) }
        assertDoesNotThrow { Settings.DEFAULT.copy(masterVolume = 1f, hitVolume = 1f, missVolume = 1f) }
        assertThrows<IllegalArgumentException> { Settings.DEFAULT.copy(masterVolume = 1.1f) }
        assertThrows<IllegalArgumentException> { Settings.DEFAULT.copy(hitVolume = -0.1f) }
        assertThrows<IllegalArgumentException> { Settings.DEFAULT.copy(missVolume = Float.NaN) }
    }

    @Test
    fun noteSpeedStaysBetweenHalfAndDouble() {
        assertDoesNotThrow { Settings.DEFAULT.copy(noteSpeed = Settings.MIN_NOTE_SPEED) }
        assertDoesNotThrow { Settings.DEFAULT.copy(noteSpeed = Settings.MAX_NOTE_SPEED) }
        assertThrows<IllegalArgumentException> { Settings.DEFAULT.copy(noteSpeed = 0.25f) }
        assertThrows<IllegalArgumentException> { Settings.DEFAULT.copy(noteSpeed = 2.5f) }
    }

    @Test
    fun effectsAreScaledByTheAppVolume() {
        val settings = Settings.DEFAULT.copy(masterVolume = 0.5f, hitVolume = 0.6f, missVolume = 1f)

        assertEquals(0.3f, settings.effectiveHitVolume, 1e-6f)
        assertEquals(0.5f, settings.effectiveMissVolume, 1e-6f)
    }

    @Test
    fun theLookOfTodayAndTheMapImageAreTheDefaults() {
        val settings = Settings.DEFAULT

        assertEquals(TrackLook.DEFAULT, settings.trackLook)
        assertEquals(
            TrackLook(HighwayBackground.STAGE, HighwayArt.NONE, NoteStyle.DISCS, FretStyle.DISCS),
            TrackLook.DEFAULT,
        )
        assertTrue(settings.showChartImage)
        assertTrue(settings.askSeparationAlways)
    }
}
