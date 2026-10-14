package com.rodrigomachado.riff.core.ui.stage

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Formas de rock em tamanho 1 (de −1 a 1), centradas em (0, 0), com y para baixo. Usadas só ao gerar texturas e
 * miniaturas, fora do laço de quadros.
 */
object RockShapes {
    /** Palheta com a ponta para baixo. */
    fun pick(path: Path) {
        path.rewind()
        path.moveTo(0f, 1.15f)
        path.cubicTo(-0.55f, 0.75f, -1f, 0.1f, -0.95f, -0.45f)
        path.cubicTo(-0.9f, -0.95f, 0.9f, -0.95f, 0.95f, -0.45f)
        path.cubicTo(1f, 0.1f, 0.55f, 0.75f, 0f, 1.15f)
        path.close()
    }

    fun star(path: Path, points: Int = 5, inner: Float = 0.45f) {
        path.rewind()
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) 1f else inner
            val a = -PI / 2 + i * PI / points
            val x = (r * cos(a)).toFloat()
            val y = (r * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    fun bolt(path: Path) {
        path.rewind()
        path.moveTo(BOLT[0], BOLT[1])
        for (i in 2 until BOLT.size step 2) path.lineTo(BOLT[i], BOLT[i + 1])
        path.close()
    }

    /** Língua de fogo com a base em y = 1. */
    fun flame(path: Path) {
        path.rewind()
        path.moveTo(-1f, 1f)
        path.cubicTo(-1f, 0.2f, -0.6f, -0.1f, -0.45f, -0.7f)
        path.cubicTo(-0.25f, -0.2f, -0.1f, -0.3f, 0f, -1f)
        path.cubicTo(0.15f, -0.4f, 0.35f, -0.3f, 0.5f, -0.75f)
        path.cubicTo(0.65f, -0.1f, 1f, 0.2f, 1f, 1f)
        path.close()
    }

    /** Caveira de frente: crânio, mandíbula, olhos, nariz e dentes vazados com [holes]. */
    fun skull(canvas: Canvas, paint: Paint, cx: Float, cy: Float, s: Float, holes: Paint) {
        canvas.drawOval(RectF(cx - s, cy - s, cx + s, cy + s * 0.75f), paint)
        canvas.drawRoundRect(
            RectF(cx - s * 0.55f, cy + s * 0.4f, cx + s * 0.55f, cy + s * 1.05f),
            s * 0.15f,
            s * 0.15f,
            paint,
        )
        for (dx in floatArrayOf(-0.4f, 0.4f)) {
            canvas.drawOval(
                RectF(cx + dx * s - s * 0.27f, cy - s * 0.05f, cx + dx * s + s * 0.27f, cy + s * 0.42f),
                holes,
            )
        }
        val nose = Path().apply {
            moveTo(cx, cy + s * 0.42f)
            lineTo(cx - s * 0.12f, cy + s * 0.62f)
            lineTo(cx + s * 0.12f, cy + s * 0.62f)
            close()
        }
        canvas.drawPath(nose, holes)
        val teeth = Paint(holes).apply { strokeWidth = max(1f, s * 0.06f) }
        for (dx in floatArrayOf(-0.3f, -0.1f, 0.1f, 0.3f)) {
            canvas.drawLine(cx + dx * s, cy + s * 0.75f, cx + dx * s, cy + s * 1.02f, teeth)
        }
    }

    /** Pontos do raio (x, y alternados). */
    internal val BOLT =
        floatArrayOf(0.15f, -1f, -0.45f, 0.1f, -0.02f, 0.1f, -0.2f, 1f, 0.5f, -0.15f, 0.05f, -0.15f, 0.3f, -1f)
}
