package com.rodrigomachado.riff.core.separation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DemucsStftTest {
    private val input = Fixtures.read("stft_in") // [2][5000]
    private val stft = DemucsStft(5000)

    private fun channel(c: Int) = input.copyOfRange(c * 5000, (c + 1) * 5000)

    @Test
    fun theSpectrumMatchesTheDemucsOne() {
        val expected = Fixtures.read("stft_spec") // [2 canais][real|imag][2048][5]
        val plane = Demucs.BINS * stft.frames
        val out = FloatArray(2 * 2 * plane)
        stft.forward(channel(0), out, offset = 0)
        stft.forward(channel(1), out, offset = 2 * plane)

        assertEquals(5, stft.frames)
        assertArrayEquals(expected, out, 1e-4f)
    }

    @Test
    fun theInverseMatchesTheDemucsOne() {
        val expected = Fixtures.read("stft_inverse")
        val spec = FloatArray(2 * Demucs.BINS * stft.frames)
        val back = FloatArray(5000)
        stft.forward(channel(1), spec, offset = 0)
        stft.inverse(spec, offset = 0, out = back)

        assertArrayEquals(expected.copyOfRange(5000, 10_000), back, 1e-4f)
    }
}
