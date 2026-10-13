package com.rodrigomachado.riff.core.separation

import org.junit.Assert.assertEquals
import org.junit.Test

class EncoderDelayTest {
    @Test
    fun theLagIsWhereTheClickCameOutMinusWhereItWent() {
        val decoded = FloatArray(30_000)
        decoded[EncoderDelay.CLICK_AT + 2048] = 0.9f
        decoded[EncoderDelay.CLICK_AT + 2049] = -0.5f

        assertEquals(2048, EncoderDelay.lagOf(decoded))
    }

    @Test
    fun aDecoderWithoutDelayGivesZero() {
        val decoded = FloatArray(30_000)
        decoded[EncoderDelay.CLICK_AT] = 1f

        assertEquals(0, EncoderDelay.lagOf(decoded))
    }

    @Test
    fun skippingDropsTheFirstFramesAcrossBlocks() {
        val inner = CollectingSink()
        val sink = SkippingStemSink(inner, skipFrames = 3)

        sink.write(floatArrayOf(1f, 1f, 2f, 2f), floatArrayOf(0f, 0f, 0f, 0f), 2)
        sink.write(floatArrayOf(3f, 3f, 4f, 4f, 5f, 5f), floatArrayOf(9f, 9f, 8f, 8f, 7f, 7f), 3)

        assertEquals(listOf(4f, 4f, 5f, 5f), inner.guitar)
        assertEquals(listOf(8f, 8f, 7f, 7f), inner.backing)
    }
}
