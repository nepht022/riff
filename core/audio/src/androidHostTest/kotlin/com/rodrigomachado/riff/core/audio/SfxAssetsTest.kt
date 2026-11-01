package com.rodrigomachado.riff.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.sound.sampled.AudioSystem
import kotlin.math.abs
import kotlin.math.pow

/** Os efeitos são gerados por scripts/gerar_sfx.py; este teste garante formato, duração e volume. */
class SfxAssetsTest {
    private fun file(name: String) = File("src/androidMain/res/raw/$name")

    private fun check(name: String, maxSeconds: Double) {
        val format = AudioSystem.getAudioFileFormat(file(name))
        assertEquals(44_100f, format.format.sampleRate)
        assertEquals(1, format.format.channels)
        assertEquals(16, format.format.sampleSizeInBits)
        val seconds = format.frameLength / format.format.sampleRate.toDouble()
        assertTrue("$name dura $seconds s", seconds in 0.01..maxSeconds)
    }

    /** Maior amostra em valor absoluto (16 bits, little-endian). */
    private fun peak(name: String): Int = AudioSystem.getAudioInputStream(file(name)).use { stream ->
        val bytes = stream.readAllBytes()
        var max = 0
        for (i in 0 until bytes.size - 1 step 2) {
            val sample = ((bytes[i].toInt() and 0xFF) or (bytes[i + 1].toInt() shl 8)).toShort().toInt()
            max = maxOf(max, abs(sample))
        }
        max
    }

    @Test
    fun eachLaneHasAShortQuietGuitarNote() {
        val limit = (Short.MAX_VALUE * 10.0.pow(-12.0 / 20)).toInt() // −12 dBFS
        for (lane in 1..5) {
            check("sfx_hit_$lane.wav", maxSeconds = 0.3)
            assertTrue("sfx_hit_$lane.wav alto demais", peak("sfx_hit_$lane.wav") <= limit)
        }
    }

    @Test
    fun eachLaneSoundsDifferent() {
        val sounds = (1..5).map { file("sfx_hit_$it.wav").readBytes().toList() }
        assertEquals(5, sounds.toSet().size)
        assertNotEquals(sounds[0], sounds[4])
    }

    @Test
    fun theOldClickIsGone() = assertFalse(file("sfx_hit.wav").exists())

    @Test
    fun eachMissVariationIsShortAndAboveTheHitSound() {
        val limit = (Short.MAX_VALUE * 10.0.pow(-9.0 / 20)).toInt() + 1 // −9 dBFS
        for (variation in 1..6) {
            check("sfx_miss_$variation.wav", maxSeconds = 0.4)
            assertTrue("sfx_miss_$variation.wav alto demais", peak("sfx_miss_$variation.wav") <= limit)
        }
    }

    @Test
    fun theSynthesizedMissIsGone() = assertFalse(file("sfx_miss.wav").exists())
}
