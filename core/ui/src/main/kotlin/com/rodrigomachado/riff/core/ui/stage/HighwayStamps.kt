package com.rodrigomachado.riff.core.ui.stage

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.rodrigomachado.riff.core.model.HighwayArt

/** Estampas claras da pista (design da pista), desenhadas num ladrilho transparente de [HighwayTexture]. */
internal object HighwayStamps {
    private const val STRETCH = 1.6f

    fun draw(kind: HighwayArt, canvas: Canvas, ink: Paint, holes: Paint) {
        val cx = HighwayTexture.WIDTH / 2f
        val depth = HighwayTexture.DEPTH.toFloat()
        // Esticadas na profundidade: a perspectiva da pista as achata, e assim elas ficam legíveis.
        canvas.save()
        canvas.scale(1f, STRETCH, 0f, 0f)
        draw(kind, canvas, ink, holes, cx, depth / STRETCH)
        canvas.restore()
    }

    private fun draw(kind: HighwayArt, canvas: Canvas, ink: Paint, holes: Paint, cx: Float, depth: Float) {
        when (kind) {
            HighwayArt.WINGED_SKULL -> {
                wings(canvas, ink, cx, depth / 2f, 34f)
                RockShapes.skull(canvas, ink, cx, depth / 2f, 34f, holes)
            }

            HighwayArt.BOLTS -> {
                shape(canvas, ink, cx - 60f, depth * 0.3f, 50f) { RockShapes.bolt(it) }
                shape(canvas, ink, cx + 60f, depth * 0.78f, 50f) { RockShapes.bolt(it) }
            }

            HighwayArt.STARS -> for ((dx, dy, s) in STARS) {
                shape(canvas, ink, cx + dx, depth * dy, s) { RockShapes.star(it) }
            }

            HighwayArt.GUITARS -> {
                guitar(canvas, ink, holes, cx - 60f, depth * 0.32f, 34f)
                guitar(canvas, ink, holes, cx + 60f, depth * 0.8f, 34f)
            }

            HighwayArt.PICKS -> for ((dx, dy) in PICKS) {
                shape(canvas, ink, cx + dx, depth * dy, 40f) { RockShapes.pick(it) }
                shape(canvas, holes, cx + dx, depth * dy, 20f) { RockShapes.bolt(it) }
            }

            HighwayArt.TATTOO -> tattoo(canvas, ink, cx, depth / 2f, 46f)

            HighwayArt.NONE -> Unit
        }
    }

    private val STARS = listOf(
        Triple(-70f, 0.15f, 26f),
        Triple(10f, 0.4f, 40f),
        Triple(75f, 0.62f, 24f),
        Triple(-40f, 0.82f, 20f),
    )
    private val PICKS = listOf(-60f to 0.22f, 60f to 0.55f, -10f to 0.84f)

    private fun shape(canvas: Canvas, paint: Paint, cx: Float, cy: Float, s: Float, build: (Path) -> Unit) {
        val path = Path().also(build)
        path.transform(
            Matrix().apply {
                postScale(s, s)
                postTranslate(cx, cy)
            },
        )
        canvas.drawPath(path, paint)
    }

    private fun wings(canvas: Canvas, paint: Paint, cx: Float, cy: Float, s: Float) {
        for (side in intArrayOf(-1, 1)) {
            for (k in 0 until 4) {
                val y = cy - s * 0.3f + k * s * 0.28f
                val x0 = cx + side * s * 0.8f
                val feather = Path().apply {
                    moveTo(x0, y)
                    lineTo(x0 + side * s * (2.2f - k * 0.35f), y - s * 0.35f)
                    lineTo(x0 + side * s * (2.0f - k * 0.35f), y + s * 0.12f)
                    close()
                }
                canvas.drawPath(feather, paint)
            }
        }
    }

    private fun guitar(canvas: Canvas, paint: Paint, holes: Paint, cx: Float, cy: Float, s: Float) {
        canvas.drawOval(RectF(cx - s * 0.75f, cy + s * 0.15f, cx + s * 0.75f, cy + s * 1.4f), paint)
        canvas.drawOval(RectF(cx - s * 0.55f, cy - s * 0.45f, cx + s * 0.55f, cy + s * 0.6f), paint)
        canvas.drawRect(cx - s * 0.12f, cy - s * 1.7f, cx + s * 0.12f, cy - s * 0.3f, paint)
        canvas.drawRect(cx - s * 0.25f, cy - s * 2.2f, cx + s * 0.25f, cy - s * 1.7f, paint)
        canvas.drawOval(RectF(cx - s * 0.2f, cy + s * 0.45f, cx + s * 0.2f, cy + s * 0.85f), holes)
    }

    private fun tattoo(canvas: Canvas, paint: Paint, cx: Float, cy: Float, s: Float) {
        // Coração com uma adaga atravessada.
        val heart = Path().apply {
            addOval(RectF(cx - s, cy - s * 0.6f, cx, cy + s * 0.3f), Path.Direction.CW)
            addOval(RectF(cx, cy - s * 0.6f, cx + s, cy + s * 0.3f), Path.Direction.CW)
            moveTo(cx - s * 0.97f, cy - s * 0.05f)
            lineTo(cx + s * 0.97f, cy - s * 0.05f)
            lineTo(cx, cy + s * 1.1f)
            close()
            op(Path(), Path.Op.UNION)
        }
        canvas.drawPath(heart, paint)
        val blade = Paint(paint).apply {
            strokeWidth = s * 0.16f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx - s * 1.5f, cy + s * 0.9f, cx + s * 1.4f, cy - s * 1.05f, blade)
    }
}
