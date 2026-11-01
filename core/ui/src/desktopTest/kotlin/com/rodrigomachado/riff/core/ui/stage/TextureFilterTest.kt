package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import org.junit.Assert.assertTrue
import org.junit.Test

/** No PC, a textura da pista esticada sai filtrada (degradê entre as cores), não em blocos de pixel. */
class TextureFilterTest {
    @Test
    fun aStretchedTileIsFilteredNotBlocky() {
        val tile = ImageBitmap(2, 2).also {
            val canvas = Canvas(it)
            canvas.drawRect(Rect(0f, 0f, 1f, 2f), Paint().apply { color = Color.Black })
            canvas.drawRect(Rect(1f, 0f, 2f, 2f), Paint().apply { color = Color.White })
        }
        val out = ImageBitmap(100, 2)
        Canvas(out).run {
            scale(50f, 1f)
            drawRect(Rect(0f, 0f, 2f, 2f), texturePaint().apply { shader = tiledShader(tile) })
        }

        val pixels = out.toPixelMap()
        val levels = (0 until 100).map { (pixels[it, 1].red * 255).toInt() }.toSet()
        assertTrue("tons na faixa esticada: $levels", levels.size > 10)
    }
}
