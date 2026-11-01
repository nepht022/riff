package com.rodrigomachado.riff.core.audio

import com.rodrigomachado.riff.core.model.MenuSound
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineUnavailableException

class DesktopSfxTest {
    @Test
    fun everySoundOfTheAppLoadsOnThePc() {
        val formats = DesktopSounds.ALL.map { name ->
            AudioSystem.getAudioInputStream(DesktopSounds.open(name)).use { it.format.sampleRate }
        }

        assertEquals(16, formats.size)
    }

    @Test
    fun withoutAnOutputTheSoundsAreSilentNotCrashes() {
        val noClips: () -> javax.sound.sampled.Clip = { throw LineUnavailableException("sem placa de som") }

        ClipSfxPlayer(noClips).run {
            hit(0)
            miss()
            setVolumes(1f, 1f)
            release()
        }
        ClipMenuSounds(noClips).play(MenuSound.NAVIGATE, 1f)
    }
}
