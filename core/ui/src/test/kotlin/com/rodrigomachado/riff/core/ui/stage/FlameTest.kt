package com.rodrigomachado.riff.core.ui.stage

import org.junit.Assert.assertEquals
import org.junit.Test

class FlameTest {
    @Test
    fun startsInvisibleAndEndsInvisible() {
        assertEquals(0f, flameFrame(0f).alpha, 0.0001f)
        assertEquals(0f, flameFrame(1f).alpha, 0.0001f)
    }

    @Test
    fun peaksEarly() {
        val peak = flameFrame(0.15f)
        assertEquals(1f, peak.alpha, 0.0001f)
        assertEquals(1.15f, peak.scaleY, 0.0001f)
    }

    @Test
    fun interpolatesBetweenKeyframes() {
        val middle = flameFrame(0.325f) // metade entre 0,15 (1; 1,15) e 0,5 (0,9; 0,95)
        assertEquals(0.95f, middle.alpha, 0.0001f)
        assertEquals(1.05f, middle.scaleY, 0.0001f)
    }

    @Test
    fun outOfRangeOrNaNProgressDrawsNothing() {
        listOf(-0.1f, 1.5f, Float.NaN).forEach { progress ->
            assertEquals(FlameFrame(alpha = 0f, scaleY = 0f), flameFrame(progress))
        }
    }

    @Test
    fun alphaAndHeightWithoutCreatingAFrameMatchTheKeyframes() {
        for (progress in listOf(0f, 0.1f, 0.15f, 0.3f, 0.5f, 0.75f, 1f, 1.5f, Float.NaN)) {
            val frame = flameFrame(progress)
            assertEquals(frame.alpha, flameAlpha(progress), 1e-6f)
            assertEquals(frame.scaleY, flameScaleY(progress), 1e-6f)
        }
    }
}
