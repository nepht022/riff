package com.rodrigomachado.riff.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Ícones da tela inicial, desenhados em código (o app não usa imagens prontas). */
@Composable
internal fun HomeIcon(modifier: Modifier, color: Color, draw: DrawScope.(Color) -> Unit) {
    Canvas(modifier) { draw(color) }
}

/** Colcheia: a cabeça inclinada, a haste e a bandeirola. */
internal fun DrawScope.drawNote(color: Color) {
    val s = min(size.width, size.height)
    val head = Size(s * 0.42f, s * 0.3f)
    val headTopLeft = Offset(s * 0.12f, s * 0.64f)
    rotate(-20f, Offset(headTopLeft.x + head.width / 2, headTopLeft.y + head.height / 2)) {
        drawOval(color, headTopLeft, head)
    }
    val stemX = s * 0.5f
    drawLine(color, Offset(stemX, s * 0.76f), Offset(stemX, s * 0.06f), s * 0.08f, StrokeCap.Round)
    val flag = Path().apply {
        moveTo(stemX, s * 0.06f)
        cubicTo(s * 0.7f, s * 0.16f, s * 0.9f, s * 0.3f, s * 0.78f, s * 0.5f)
        cubicTo(s * 0.8f, s * 0.36f, s * 0.68f, s * 0.28f, stemX, s * 0.26f)
        close()
    }
    drawPath(flag, color)
}

/** Bandeira no mastro: a sala onde os amigos se encontram. */
internal fun DrawScope.drawFlag(color: Color) {
    val s = min(size.width, size.height)
    drawLine(color, Offset(s * 0.22f, s * 0.08f), Offset(s * 0.22f, s * 0.94f), s * 0.08f, StrokeCap.Round)
    val cloth = Path().apply {
        moveTo(s * 0.26f, s * 0.1f)
        cubicTo(s * 0.5f, s * 0.02f, s * 0.6f, s * 0.2f, s * 0.86f, s * 0.12f)
        lineTo(s * 0.86f, s * 0.5f)
        cubicTo(s * 0.6f, s * 0.58f, s * 0.5f, s * 0.4f, s * 0.26f, s * 0.48f)
        close()
    }
    drawPath(cloth, color)
}

/** Envelope: o Reportar. */
internal fun DrawScope.drawEnvelope(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(w * 0.09f)
    drawRect(color, Offset(w * 0.06f, h * 0.2f), Size(w * 0.88f, h * 0.6f), style = stroke)
    val flap = Path().apply {
        moveTo(w * 0.06f, h * 0.2f)
        lineTo(w * 0.5f, h * 0.56f)
        lineTo(w * 0.94f, h * 0.2f)
    }
    drawPath(flap, color, style = stroke)
}

/** Engrenagem: as Configurações. */
internal fun DrawScope.drawGear(color: Color) {
    val c = Offset(size.width / 2, size.height / 2)
    val r = min(size.width, size.height) / 2
    repeat(TEETH) { i ->
        rotate(i * 360f / TEETH, c) {
            drawRect(color, Offset(c.x - r * 0.14f, c.y - r), Size(r * 0.28f, r * 0.4f))
        }
    }
    drawCircle(color, r * 0.7f, c)
    drawCircle(Color.Black, r * 0.28f, c)
}

/** Triângulo do "tocar": o Continuar. */
internal fun DrawScope.drawPlay(color: Color) {
    val w = size.width
    val h = size.height
    val play = Path().apply {
        moveTo(w * 0.22f, h * 0.12f)
        lineTo(w * 0.88f, h * 0.5f)
        lineTo(w * 0.22f, h * 0.88f)
        close()
    }
    drawPath(play, color)
}

/** Estrela de cinco pontas: o Ranking. */
internal fun DrawScope.drawStar(color: Color) {
    val s = min(size.width, size.height)
    val c = Offset(size.width / 2, size.height / 2)
    val star = Path()
    repeat(STAR_POINTS * 2) { i ->
        // Pontas e vales alternados, começando pela ponta de cima.
        val radius = if (i % 2 == 0) s * 0.5f else s * 0.2f
        val angle = PI * i / STAR_POINTS - PI / 2
        val point = Offset(c.x + radius * cos(angle).toFloat(), c.y + radius * sin(angle).toFloat())
        if (i == 0) star.moveTo(point.x, point.y) else star.lineTo(point.x, point.y)
    }
    star.close()
    drawPath(star, color)
}

private const val TEETH = 8
private const val STAR_POINTS = 5
