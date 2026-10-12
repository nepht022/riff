package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Raio zero ou NaN (Canvas ainda sem tamanho, profundidade fora da pista) não pode derrubar o app. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StageDrawingTest {
    /** Desenha de verdade num bitmap: um `Canvas` de Compose no Robolectric não chega a desenhar sem um quadro. */
    private fun draw(block: DrawScope.() -> Unit) {
        var drawn = false
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(100, 100)), Size(100f, 100f)) {
            block()
            drawn = true
        }
        assertTrue(drawn)
    }

    @Test
    fun gemAndButtonWithZeroOrNaNRadiusDrawNothingWithoutCrashing() {
        val cache = StageCache()
        draw {
            listOf(0f, -1f, Float.NaN).forEach { radius ->
                drawGem(cache, Offset(50f, 50f), radius, lane = 0)
                drawFretButton(cache, Offset(50f, 50f), radius, lane = 1, pressed = true)
                drawFretButton(cache, Offset(50f, 50f), radius, lane = 1, pressed = false)
            }
        }
    }

    @Test
    fun aFlameWithZeroOrNaNSizeDrawsNothing() {
        val cache = StageCache()
        draw {
            listOf(0f to 140f, 50f to 0f, Float.NaN to 140f, 50f to Float.NaN).forEach { (width, height) ->
                drawFlame(cache, Offset(50f, 90f), width, height, progress = 0.3f)
            }
        }

        // Com altura 0 o contorno viraria infinito (raio dividido pela altura): nada é montado.
        assertTrue(cache.flamePaths.all { it.isEmpty })
    }
}
