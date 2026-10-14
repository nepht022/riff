package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.geometry.Size
import com.rodrigomachado.riff.core.model.HighwayBackground
import com.rodrigomachado.riff.core.model.TrackLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StageCacheTest {
    @Test
    fun theSameSizeKeepsTheSameObjects() {
        val cache = StageCache()
        val geometry = cache.update(Size(720f, 1280f), density = 2f)
        val brush = cache.highwayBrush
        val stroke = cache.outerStroke

        assertSame(geometry, cache.update(Size(720f, 1280f), density = 2f))
        assertSame(brush, cache.highwayBrush)
        assertSame(stroke, cache.outerStroke)
    }

    @Test
    fun aNewSizeRebuildsTheGeometryAndTheHighway() {
        val cache = StageCache()
        val portrait = cache.update(Size(720f, 1280f), density = 2f)
        val brush = cache.highwayBrush

        val landscape = cache.update(Size(1280f, 720f), density = 2f)

        assertNotSame(portrait, landscape)
        assertNotSame(brush, cache.highwayBrush)
        assertEquals(1280f, landscape.size.width)
    }

    @Test
    fun aNewDensityRebuildsTheStrokes() {
        val cache = StageCache()
        cache.update(Size(720f, 1280f), density = 2f)

        cache.update(Size(720f, 1280f), density = 3f)

        assertEquals(21f, cache.outerStroke.width) // 7 dp
        assertEquals(3f, cache.laneLineWidth) // 1 dp
    }

    @Test
    fun everyLaneHasItsGemAndButtonBrushesReadyAhead() {
        val cache = StageCache()

        for (lane in 0 until 5) {
            assertNotSame(cache.gemCap(lane, missed = false), cache.gemCap(lane, missed = true))
            assertNotSame(cache.fretInner(lane, pressed = true), cache.fretInner(lane, pressed = false))
            assertSame(cache.gemCap(lane, missed = false), cache.gemCap(lane, missed = false))
        }
    }

    @Test
    fun changingTheLookRebuildsTheTexturesOnlyThen() {
        val cache = StageCache()
        cache.update(Size(400f, 800f), 1f)
        assertNull(cache.backgroundShader)

        cache.look = TrackLook.DEFAULT.copy(background = HighwayBackground.NEON)
        cache.update(Size(400f, 800f), 1f)
        val first = cache.backgroundShader
        cache.look = TrackLook.DEFAULT.copy(background = HighwayBackground.NEON)
        cache.update(Size(400f, 800f), 1f)

        assertNotNull(first)
        assertSame(first, cache.backgroundShader)
    }
}
