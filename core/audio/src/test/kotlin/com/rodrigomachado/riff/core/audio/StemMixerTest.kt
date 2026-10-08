package com.rodrigomachado.riff.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StemMixerTest {
    private fun constant(value: Float, frames: Int, channels: Int = 1) = FloatArray(frames * channels) { value }

    @Test
    fun tracksAreSummed() {
        val mixer = StemMixer(trackCount = 2, rampFrames = 10)
        val out = FloatArray(8)

        mixer.mix(arrayOf(constant(0.2f, 4), constant(0.3f, 4)), intArrayOf(1, 1), intArrayOf(4, 4), 4, out)

        out.forEach { assertEquals(0.5f, it, 1e-6f) }
    }

    @Test
    fun monoIsCopiedToBothChannelsAndStereoIsKept() {
        val mixer = StemMixer(trackCount = 2, rampFrames = 10)
        val stereo = floatArrayOf(0.2f, -0.2f, 0.2f, -0.2f)
        val out = FloatArray(4)

        mixer.mix(arrayOf(constant(0.1f, 2), stereo), intArrayOf(1, 2), intArrayOf(2, 2), 2, out)

        assertArrayEquals(floatArrayOf(0.3f, -0.1f, 0.3f, -0.1f), out, 1e-6f)
    }

    @Test
    fun cuttingATrackRampsDownWithoutJumpsAndComesBack() {
        val mixer = StemMixer(trackCount = 1, rampFrames = 10)
        val out = FloatArray(40)
        mixer.setTargetGain(0, 0f)

        mixer.mix(arrayOf(constant(1f, 20)), intArrayOf(1), intArrayOf(20), 20, out)

        val left = (0 until 20).map { out[it * 2] }
        assertEquals(0.9f, left[0], 1e-5f)
        assertEquals(0f, left[9], 1e-5f)
        assertEquals(0f, left[19], 1e-5f)
        left.zipWithNext().forEach { (a, b) -> assertTrue("salto de $a para $b", abs(a - b) <= 0.1f + 1e-5f) }

        mixer.setTargetGain(0, 1f)
        mixer.mix(arrayOf(constant(1f, 20)), intArrayOf(1), intArrayOf(20), 20, out)
        assertEquals(0.1f, out[0], 1e-5f)
        assertEquals(1f, out[19 * 2], 1e-5f)
    }

    @Test
    fun theRampContinuesAcrossBlocks() {
        val mixer = StemMixer(trackCount = 1, rampFrames = 10)
        val out = FloatArray(8)
        mixer.setTargetGain(0, 0f)

        mixer.mix(arrayOf(constant(1f, 4)), intArrayOf(1), intArrayOf(4), 4, out)
        assertEquals(0.6f, mixer.currentGain(0), 1e-5f)
        mixer.mix(arrayOf(constant(1f, 4)), intArrayOf(1), intArrayOf(4), 4, out)
        assertEquals(0.2f, mixer.currentGain(0), 1e-5f)
    }

    @Test
    fun aTrackPastItsEndIsSilence() {
        val mixer = StemMixer(trackCount = 2, rampFrames = 10)
        val out = FloatArray(16)

        mixer.mix(arrayOf(constant(0.25f, 8), constant(0.25f, 8)), intArrayOf(1, 1), intArrayOf(8, 4), 8, out)

        assertEquals(0.5f, out[3 * 2], 1e-6f)
        assertEquals(0.25f, out[6 * 2], 1e-6f)
    }

    @Test
    fun theSumIsLimitedToTheUnitRange() {
        val mixer = StemMixer(trackCount = 3, rampFrames = 10)
        val out = FloatArray(4)

        mixer.mix(
            arrayOf(constant(0.6f, 2), constant(0.6f, 2), constant(-0.9f, 2, channels = 2).also { it[0] = 0.9f }),
            intArrayOf(1, 1, 2),
            intArrayOf(2, 2, 2),
            2,
            out,
        )

        assertEquals(1f, out[0], 1e-6f) // 0,6 + 0,6 + 0,9
        out.forEach { assertTrue(it in -1f..1f) }
    }

    @Test
    fun onlyTheRequestedFramesAreWrittenIntoTheGivenBuffer() {
        val mixer = StemMixer(trackCount = 1, rampFrames = 10)
        val out = FloatArray(10) { 7f }

        mixer.mix(arrayOf(constant(0.1f, 2)), intArrayOf(1), intArrayOf(2), 2, out)

        assertEquals(0.1f, out[3], 1e-6f)
        assertEquals(7f, out[4], 0f)
    }

    @Test
    fun thirtyMillisecondsOfRampAtCdQuality() = assertEquals(1_323, StemMixer.rampFrames(44_100))
}
