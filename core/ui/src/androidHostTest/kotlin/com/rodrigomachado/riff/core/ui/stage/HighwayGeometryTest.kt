package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.function.ThrowingRunnable

class HighwayGeometryTest {
    private val size = Size(1000f, 2000f)
    private val geometry = HighwayGeometry(size)

    @Test
    fun strikeLineHasFullScaleAtItsScreenPosition() {
        assertEquals(1f, geometry.scaleAt(0f), 0.0001f)
        assertEquals(2000f * 0.82f, geometry.yAt(0f), 0.01f)
        assertEquals(1000f * 0.92f, geometry.widthAt(0f), 0.01f)
    }

    @Test
    fun middleLaneIsAlwaysCentered() {
        listOf(0f, 1f, 3f, geometry.visibleDistance).forEach { distance ->
            assertEquals(500f, geometry.laneCenter(2, distance).x, 0.01f)
        }
    }

    @Test
    fun fartherNotesAreHigherAndNarrower() {
        assertTrue(geometry.yAt(2f) < geometry.yAt(1f))
        assertTrue(geometry.widthAt(2f) < geometry.widthAt(1f))
        assertTrue(geometry.scaleAt(geometry.visibleDistance) < geometry.scaleAt(1f))
    }

    @Test
    fun evenlySpacedNotesGetCloserOnScreenWithDistance() {
        // Encurtamento de perspectiva: a mesma distância no tempo ocupa menos tela quanto mais longe.
        val gaps = (0 until 5).map { d -> geometry.yAt(d.toFloat()) - geometry.yAt(d + 1f) }
        gaps.zipWithNext().forEach { (near, far) -> assertTrue("$near deveria ser maior que $far", near > far) }
    }

    @Test
    fun lanesSplitTheWidthEvenlyAtTheStrikeLine() {
        val left = geometry.leftAt(0f)
        assertEquals(left + geometry.laneWidthAtStrike / 2, geometry.laneCenter(0, 0f).x, 0.01f)
        assertEquals(left + geometry.laneWidthAtStrike * 4.5f, geometry.laneCenter(4, 0f).x, 0.01f)
    }

    @Test
    fun rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException::class.java, ThrowingRunnable { HighwayGeometry(size, laneCount = 0) })
        assertThrows(
            IllegalArgumentException::class.java,
            ThrowingRunnable { HighwayGeometry(size, cameraDistance = 0f) },
        )
    }

    @Test
    fun laneAtMapsTouchesToTheLaneUnderTheFingerAndClampsOutside() {
        (0 until 5).forEach { lane -> assertEquals(lane, geometry.laneAt(geometry.laneCenter(lane, 0f).x)) }
        assertEquals(0, geometry.laneAt(-50f))
        assertEquals(4, geometry.laneAt(5_000f))
    }
}
