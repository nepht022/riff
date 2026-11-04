package com.rodrigomachado.riff.core.separation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrtDemucsModelTest {
    @Test
    fun oneSegmentGivesSixSourcesInBothBranches() {
        OrtDemucsModel(Fixtures.modelBytes(), threads = 2).use { model ->
            val out = model.run(
                FloatArray(2 * Demucs.SEGMENT) { (it % 100) / 100f - 0.5f },
                FloatArray(4 * Demucs.BINS * Demucs.FRAMES),
            )

            assertEquals(Demucs.SOURCES * 4 * Demucs.BINS * Demucs.FRAMES, out.spec.size)
            assertEquals(Demucs.SOURCES * 2 * Demucs.SEGMENT, out.mix.size)
            assertTrue(out.mix.all { it.isFinite() })
        }
    }
}
