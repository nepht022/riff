package com.rodrigomachado.riff

import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuSoundGateTest {
    private val played = mutableListOf<Pair<MenuSound, Float>>()

    @Test
    fun soundsPlayAtTheAppVolume() {
        playMenuSound(
            { sound, volume -> played += sound to volume },
            Settings.DEFAULT.copy(masterVolume = 0.4f),
            MenuSound.CONFIRM,
        )

        assertEquals(listOf(MenuSound.CONFIRM to 0.4f), played)
    }

    @Test
    fun turnedOffOrStillLoadingTheyStaySilent() {
        playMenuSound(
            { sound, volume -> played += sound to volume },
            Settings.DEFAULT.copy(menuSounds = false),
            MenuSound.BACK,
        )
        playMenuSound({ sound, volume -> played += sound to volume }, null, MenuSound.NAVIGATE)

        assertEquals(emptyList<Pair<MenuSound, Float>>(), played)
    }
}
