package com.rodrigomachado.riff.feature.gameplay

import com.rodrigomachado.riff.core.ui.stage.StageCache
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StageRendererTest {
    @Test
    fun fretsEveryHalfSecondAheadOfTheSongTimeFillTheArray() {
        val cache = StageCache()

        fillFretDistances(cache, t = 1_200, visibleMs = 2_000, msPerUnit = 100f)

        assertEquals(4, cache.fretCount) // 1.500, 2.000, 2.500, 3.000
        assertArrayEquals(floatArrayOf(3f, 8f, 13f, 18f), cache.fretDistances.copyOf(4), 1e-4f)
    }

    @Test
    fun moreFretsThanTheArrayHoldsAreCappedWithoutCrashing() {
        val cache = StageCache()

        fillFretDistances(cache, t = 0, visibleMs = 1_000_000, msPerUnit = 1f)

        assertEquals(cache.fretDistances.size, cache.fretCount)
    }
}
