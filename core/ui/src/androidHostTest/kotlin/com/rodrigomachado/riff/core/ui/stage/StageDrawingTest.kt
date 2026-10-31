package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.rodrigomachado.riff.core.model.FretStyle
import com.rodrigomachado.riff.core.model.NoteStyle
import com.rodrigomachado.riff.core.model.TrackLook
import org.junit.Assert.assertEquals
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

    @Test
    fun aMissedNoteIsFainterInEveryStyle() {
        for (style in NoteStyle.entries) {
            val cache = StageCache().apply { look = TrackLook.DEFAULT.copy(notes = style) }
            val normal = paintedAlpha { drawGem(cache, Offset(50f, 60f), 30f, lane = 0) }
            val missed = paintedAlpha { drawGem(cache, Offset(50f, 60f), 30f, lane = 0, missed = true) }
            assertTrue("$style: $missed < $normal", missed < normal)
        }
    }

    @Test
    fun everyNoteAndButtonStyleDrawsSomethingInTheLaneColor() {
        for (style in NoteStyle.entries) {
            val cache = StageCache().apply { look = TrackLook.DEFAULT.copy(notes = style) }
            assertTrue("$style", greenPixels { drawGem(cache, Offset(50f, 60f), 30f, lane = 0) } > 50)
        }
        for (style in FretStyle.entries) {
            val cache = StageCache().apply { look = TrackLook.DEFAULT.copy(frets = style) }
            assertTrue("$style", greenPixels { drawFretButton(cache, Offset(50f, 50f), 30f, 0, pressed = false) } > 50)
        }
    }

    @Test
    fun eachStyleHasItsOwnShape() {
        val notes = NoteStyle.entries.map { style ->
            val cache = StageCache().apply { look = TrackLook.DEFAULT.copy(notes = style) }
            render { drawGem(cache, Offset(50f, 60f), 30f, lane = 0) }.toList()
        }
        val frets = FretStyle.entries.map { style ->
            val cache = StageCache().apply { look = TrackLook.DEFAULT.copy(frets = style) }
            render { drawFretButton(cache, Offset(50f, 50f), 30f, 0, pressed = false) }.toList()
        }

        assertEquals(NoteStyle.entries.size, notes.distinct().size)
        assertEquals(FretStyle.entries.size, frets.distinct().size)
    }

    private fun render(block: DrawScope.() -> Unit): IntArray {
        val image = ImageBitmap(100, 100)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(100f, 100f), block)
        val bitmap = image.asAndroidBitmap()
        return IntArray(100 * 100).also { bitmap.getPixels(it, 0, 100, 0, 0, 100, 100) }
    }

    private fun paintedAlpha(block: DrawScope.() -> Unit): Long = render(block).sumOf { (it ushr 24).toLong() }

    /** Pixels com o verde da trilha 0 dominando (vermelho e azul bem abaixo). */
    private fun greenPixels(block: DrawScope.() -> Unit): Int = render(block).count {
        val r = (it shr 16) and 0xFF
        val g = (it shr 8) and 0xFF
        val b = it and 0xFF
        (it ushr 24) > 0 && g > 90 && g > r + 40 && g > b + 40
    }
}
