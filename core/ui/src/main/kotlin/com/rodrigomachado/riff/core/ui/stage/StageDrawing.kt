package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.theme.RiffColors

private val ChromeLight = Color(0xFFF4F4F4)
private val ChromeMid = Color(0xFF7B7B7B)
private val ChromeDark = Color(0xFF5A5A5A)
private val GemBase = Color(0xFF151515)
private val HighwayFar = Color(0xFF000000)
private val HighwayNear = Color(0xFF3B1C16)
private val FlameRed = Color(0xFFFF3D00)
private val FlameOrange = Color(0xFFFF9D00)
private val FlameYellow = Color(0xFFFFD44D)
private val FlameCore = Color(0xFFFFFBE6)
private const val LINE_ALPHA = 0.18f

private fun chrome(center: Offset) =
    Brush.sweepGradient(listOf(ChromeLight, ChromeMid, ChromeLight, ChromeDark, ChromeLight), center)

/** Braço da pista: trapézio escuro com moldura de papel e vermelho, linhas de trilha e de traste. */
fun DrawScope.drawHighway(geometry: HighwayGeometry, fretDepths: List<Float>) {
    val outline = Path().apply {
        moveTo(geometry.leftAt(0f), 0f)
        lineTo(geometry.leftAt(0f) + geometry.widthAt(0f), 0f)
        lineTo(geometry.leftAt(1f) + geometry.widthAt(1f), size.height)
        lineTo(geometry.leftAt(1f), size.height)
        close()
    }
    drawPath(outline, Brush.verticalGradient(listOf(HighwayFar, HighwayNear)))
    val line = RiffColors.Paper.copy(alpha = LINE_ALPHA)
    for (lane in 1 until geometry.laneCount) {
        val top = Offset(geometry.leftAt(0f) + geometry.widthAt(0f) * lane / geometry.laneCount, 0f)
        val bottom = Offset(geometry.leftAt(1f) + geometry.widthAt(1f) * lane / geometry.laneCount, size.height)
        drawLine(line, top, bottom, 1.dp.toPx())
    }
    fretDepths.filter { it in 0f..1f }.forEach { depth ->
        val y = geometry.yAt(depth)
        drawLine(
            line,
            Offset(geometry.leftAt(depth), y),
            Offset(geometry.leftAt(depth) + geometry.widthAt(depth), y),
            2.dp.toPx(),
        )
    }
    drawPath(outline, RiffColors.TourRed, style = Stroke(width = 7.dp.toPx()))
    drawPath(outline, RiffColors.Paper, style = Stroke(width = 3.dp.toPx()))
}

/** Gema 3D: sombra, aro cromado, base preta e domo colorido com reflexo. HOPO tem o centro mais claro. */
fun DrawScope.drawGem(center: Offset, radius: Float, color: Color, hopo: Boolean = false) {
    drawCircle(Color.Black, radius, center + Offset(0f, radius * 0.14f))
    drawCircle(chrome(center), radius, center)
    drawCircle(GemBase, radius * 0.8f, center)
    val dome = radius * 0.62f
    drawCircle(
        Brush.radialGradient(
            0f to Color.White,
            0.35f to color,
            1f to lerp(color, Color.Black, 0.45f),
            center = center + Offset(-dome * 0.25f, -dome * 0.35f),
            radius = dome * 1.4f,
        ),
        dome,
        center,
    )
    if (hopo) drawCircle(Color.White.copy(alpha = 0.85f), dome * 0.45f, center)
    drawCircle(Color.White.copy(alpha = 0.7f), dome * 0.18f, center + Offset(-dome * 0.35f, -dome * 0.4f))
}

/** Botão da linha de acerto. Pressionado, desce e acende na cor da trilha. */
fun DrawScope.drawFretButton(center: Offset, radius: Float, color: Color, pressed: Boolean) {
    val drop = radius * 0.14f
    val c = if (pressed) center + Offset(0f, drop * 0.7f) else center
    if (pressed) {
        drawCircle(color.copy(alpha = 0.35f), radius * 1.3f, c)
    } else {
        drawCircle(Color.Black, radius, center + Offset(0f, drop))
    }
    drawCircle(chrome(c), radius, c)
    drawCircle(color, radius * 0.8f, c)
    drawCircle(GemBase, radius * 0.55f, c)
    if (pressed) {
        drawCircle(
            Brush.radialGradient(0f to Color.White, 0.5f to color, 1f to color, center = c, radius = radius * 0.5f),
            radius * 0.5f,
            c,
        )
    }
}

/** Rastro da nota longa. Brilha enquanto está sendo segurada. */
fun DrawScope.drawSustainTrail(start: Offset, end: Offset, width: Float, color: Color, active: Boolean) {
    if (active) drawLine(color.copy(alpha = 0.3f), start, end, width * 2.2f, StrokeCap.Round)
    drawLine(color.copy(alpha = if (active) 1f else 0.6f), start, end, width, StrokeCap.Round)
}

/** Chama do acerto saindo de [base] (centro do botão), no quadro dado por [progress] (0 a 1). */
fun DrawScope.drawFlame(base: Offset, width: Float, height: Float, progress: Float) {
    val frame = flameFrame(progress)
    if (frame.alpha <= 0f) return
    val h = height * frame.scaleY
    fun layer(w: Float, layerHeight: Float, dx: Float, vararg stops: Pair<Float, Color>) {
        drawPath(
            path = flameTongue(base.x + dx, base.y, w, layerHeight),
            brush = Brush.verticalGradient(*stops, startY = base.y - layerHeight, endY = base.y),
            alpha = frame.alpha,
        )
    }
    layer(width, h, 0f, 0f to FlameRed.copy(alpha = 0f), 0.3f to FlameRed, 0.7f to FlameOrange, 1f to FlameYellow)
    layer(width * 0.55f, h * 0.7f, 0f, 0f to FlameYellow.copy(alpha = 0f), 0.4f to FlameYellow, 1f to FlameCore)
    layer(width * 0.3f, h * 0.4f, -width * 0.15f, 0f to FlameYellow.copy(alpha = 0f), 1f to FlameYellow)
    SPARKS.forEach { (spread, rise) ->
        val spark = Offset(base.x + spread * width * (0.5f + progress), base.y - h * (rise + 0.6f * progress))
        drawCircle(FlameYellow, width * 0.05f, spark, alpha = frame.alpha)
    }
}

/** Faíscas: deslocamento lateral e altura relativa de partida. */
private val SPARKS = listOf(-0.45f to 0.75f, 0.1f to 0.95f, 0.4f to 0.62f)

/** Língua de fogo: base arredondada e ponta afinada para cima. */
private fun flameTongue(centerX: Float, bottom: Float, width: Float, height: Float): Path {
    val top = bottom - height
    val r = width / 2
    return Path().apply {
        moveTo(centerX, top)
        cubicTo(centerX + r * 0.2f, top + height * 0.35f, centerX + r, top + height * 0.5f, centerX + r, bottom - r)
        cubicTo(centerX + r, bottom - r * 0.45f, centerX + r * 0.55f, bottom, centerX, bottom)
        cubicTo(centerX - r * 0.55f, bottom, centerX - r, bottom - r * 0.45f, centerX - r, bottom - r)
        cubicTo(centerX - r, top + height * 0.5f, centerX - r * 0.2f, top + height * 0.35f, centerX, top)
        close()
    }
}
