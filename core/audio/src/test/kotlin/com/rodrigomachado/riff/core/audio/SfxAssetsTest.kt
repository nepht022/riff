package com.rodrigomachado.riff.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.sound.sampled.AudioSystem

/** Os efeitos são gerados por scripts/gerar_sfx.py; este teste garante formato e duração curtos. */
class SfxAssetsTest {
    private fun check(name: String, maxSeconds: Double) {
        val format = AudioSystem.getAudioFileFormat(File("src/main/res/raw/$name"))
        assertEquals(44_100f, format.format.sampleRate)
        assertEquals(1, format.format.channels)
        assertEquals(16, format.format.sampleSizeInBits)
        val seconds = format.frameLength / format.format.sampleRate.toDouble()
        assertTrue("$name dura $seconds s", seconds in 0.01..maxSeconds)
    }

    @Test
    fun hitIsAShortClick() = check("sfx_hit.wav", maxSeconds = 0.08)

    @Test
    fun missIsAShortMutedString() = check("sfx_miss.wav", maxSeconds = 0.25)
}
