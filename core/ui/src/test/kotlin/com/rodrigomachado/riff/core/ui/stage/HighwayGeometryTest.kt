package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighwayGeometryTest {
    private val geometry = HighwayGeometry(Size(1000f, 2000f), laneCount = 5, topWidthFraction = 0.4f)

    @Test
    fun middleLaneIsAlwaysCentered() {
        listOf(0f, 0.5f, 1f).forEach { depth -> assertEquals(500f, geometry.laneCenter(2, depth).x, 0.01f) }
    }

    @Test
    fun highwayNarrowsTowardTheTop() {
        assertEquals(geometry.widthAt(1f) * 0.4f, geometry.widthAt(0f), 0.01f)
        assertTrue(geometry.widthAt(0.5f) < geometry.widthAt(1f))
        assertEquals(0.4f, geometry.scaleAt(0f), 0.0001f)
        assertEquals(1f, geometry.scaleAt(1f), 0.0001f)
    }

    @Test
    fun lanesSplitTheWidthEvenly() {
        val left = geometry.leftAt(1f)
        val laneWidth = geometry.widthAt(1f) / 5
        assertEquals(left + laneWidth / 2, geometry.laneCenter(0, 1f).x, 0.01f)
        assertEquals(left + laneWidth * 4.5f, geometry.laneCenter(4, 1f).x, 0.01f)
    }

    @Test
    fun depthMapsLinearlyToHeight() {
        assertEquals(0f, geometry.yAt(0f), 0.01f)
        assertEquals(1500f, geometry.yAt(0.75f), 0.01f)
        assertEquals(1500f, geometry.laneCenter(1, 0.75f).y, 0.01f)
    }
}
