package com.rodrigomachado.riff.core.ui.stage

import android.graphics.Matrix
import androidx.compose.ui.geometry.Size
import com.rodrigomachado.riff.core.model.HighwayBackground
import com.rodrigomachado.riff.core.model.TrackLook
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max

/** A homografia do PC ([quadToQuad]) dá a mesma perspectiva que o `setPolyToPoly` do Android usa na pista. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuadToQuadAndroidTest {
    @Test
    fun theHighwayTrapezoidMatchesAndroid() {
        for (size in listOf(Size(720f, 1280f), Size(1280f, 720f), Size(1920f, 1080f))) {
            for (scroll in listOf(0f, 0.7f, 1.3f)) {
                val (src, dst) = highwayCorners(size, scroll)
                val android = FloatArray(9).also { Matrix().apply { setPolyToPoly(src, 0, dst, 0, 4) }.getValues(it) }
                val common = quadToQuad(src, dst)

                for (i in 0 until 9) {
                    val expected = android[i] / android[8]
                    val tolerance = 1e-3f * max(1f, abs(expected))
                    assertEquals("$size, rolagem $scroll, valor $i", expected, common[i], tolerance)
                }
            }
        }
    }

    /** Os cantos que a `StageCache` passa para a perspectiva: a faixa do ladrilho e o trapézio da pista. */
    private fun highwayCorners(size: Size, scroll: Float): Pair<FloatArray, FloatArray> {
        val cache = StageCache()
        cache.look = TrackLook.DEFAULT.copy(background = HighwayBackground.NEON)
        cache.update(size, density = 2f)
        cache.scrollDistance = scroll
        cache.updateTextureMatrix()
        return cache.polySrc to cache.polyDst
    }
}
