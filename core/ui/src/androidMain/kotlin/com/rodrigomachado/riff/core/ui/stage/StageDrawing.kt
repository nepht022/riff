package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.rodrigomachado.riff.core.model.FretStyle
import com.rodrigomachado.riff.core.model.NoteStyle
import com.rodrigomachado.riff.core.ui.theme.LaneColors
import com.rodrigomachado.riff.core.ui.theme.RiffColors

internal val ChromeLight = Color(0xFFF4F4F4)
internal val ChromeMid = Color(0xFF7B7B7B)
internal val HighwayFar = Color(0xFF000000)
internal val HighwayNear = Color(0xFF3B1C16)
internal val FlameRed = Color(0xFFFF3D00)
internal val FlameOrange = Color(0xFFFF9D00)
internal val FlameYellow = Color(0xFFFFD44D)
internal val FlameCore = Color(0xFFFFFBE6)
internal const val LINE_ALPHA = 0.18f

/** Achatamento vertical de gemas e alvos: eles estão deitados na pista, vistos em ângulo. */
internal const val TILT = 0.42f
internal const val WEAVE_ALPHA = 0.06f
internal const val WEAVE_LONGITUDINAL = 40
internal const val WEAVE_STEP = 0.12f

/** Gema perdida: só as partes coloridas esmaecem (sombra e aro cromado não). */
const val MISSED_GEM_ALPHA = 0.4f
internal const val GEM_SIDE = 0.32f
internal const val FRET_INNER = 0.6f
internal const val FLAME_LAYERS = 3

private fun DrawScope.oval(color: Color, center: Offset, rx: Float, ry: Float) =
    drawOval(color, Offset(center.x - rx, center.y - ry), Size(rx * 2, ry * 2))

private fun DrawScope.oval(brush: Brush, center: Offset, rx: Float, ry: Float) =
    drawOval(brush, Offset(center.x - rx, center.y - ry), Size(rx * 2, ry * 2))

/**
 * Braço em perspectiva: superfície (a de sempre ou a textura escolhida, rolando com a música), estampa, trilhas e
 * trastes do [cache] e a moldura nas cores do fundo.
 */
fun DrawScope.drawHighway(cache: StageCache) {
    val geometry = cache.geometry
    val far = geometry.visibleDistance
    val near = geometry.nearDistance
    cache.updateTextureMatrix()
    val background = cache.backgroundShader
    if (background == null) {
        drawStageSurface(cache)
    } else {
        drawTexture(cache, background)
        drawPath(cache.outline, cache.fade)
    }
    cache.artShader?.let { drawTexture(cache, it) }
    val line = RiffColors.Paper.copy(alpha = LINE_ALPHA)
    for (lane in 1 until geometry.laneCount) {
        along(geometry, lane.toFloat() / geometry.laneCount, line, cache.laneLineWidth)
    }
    for (i in 0 until cache.fretCount) {
        val fret = cache.fretDistances[i]
        if (fret in near..far) across(geometry, fret, line, cache.fretLineWidth)
    }
    drawPath(cache.outline, cache.edges.outer, style = cache.outerStroke)
    drawPath(cache.outline, cache.edges.inner, style = cache.innerStroke)
}

internal fun DrawScope.across(geometry: HighwayGeometry, distance: Float, color: Color, width: Float) = drawLine(
    color,
    Offset(geometry.leftAt(distance), geometry.yAt(distance)),
    Offset(geometry.leftAt(distance) + geometry.widthAt(distance), geometry.yAt(distance)),
    width,
)

internal fun DrawScope.along(geometry: HighwayGeometry, fraction: Float, color: Color, width: Float) {
    val far = geometry.visibleDistance
    val near = geometry.nearDistance
    drawLine(
        color,
        Offset(geometry.leftAt(far) + geometry.widthAt(far) * fraction, geometry.yAt(far)),
        Offset(geometry.leftAt(near) + geometry.widthAt(near) * fraction, geometry.yAt(near)),
        width,
    )
}

/**
 * Gema: disco visto de lado, apoiado em [center] (o ponto da trilha). [radius] é o raio horizontal. Desenhada
 * em tamanho 1 e escalada, com os pincéis prontos do [cache]: nada é criado por quadro.
 */
fun DrawScope.drawGem(
    cache: StageCache,
    center: Offset,
    radius: Float,
    lane: Int,
    missed: Boolean = false,
    hopo: Boolean = false,
) {
    if (!(radius > 0f)) return // zero, negativo ou NaN: nada a desenhar
    val base = LaneColors.forLane(lane)
    val color = if (missed) base.copy(alpha = MISSED_GEM_ALPHA) else base
    if (cache.look.notes != NoteStyle.DISCS) return drawStyledGem(cache, cache.look.notes, center, radius, color, hopo)
    val top = Offset(0f, -GEM_SIDE)
    withTransform({
        translate(center.x, center.y)
        scale(radius, radius, Offset.Zero)
    }) {
        oval(Color.Black.copy(alpha = 0.6f), Offset(0f, GEM_SIDE * 0.6f), 1.05f, TILT * 1.05f)
        oval(lerp(color, Color.Black, 0.35f), Offset.Zero, 1f, TILT)
        drawRect(color, Offset(-1f, top.y), Size(2f, GEM_SIDE))
        oval(cache.chrome, top, 1f, TILT)
        oval(cache.gemCap(lane, missed), top, 0.72f, TILT * 0.72f)
        if (hopo) oval(Color.White.copy(alpha = 0.85f), top, 0.32f, TILT * 0.32f)
    }
}

/** Alvo deitado na linha de acerto: anel com aro cromado e centro escuro; pressionado, acende na cor da trilha. */
fun DrawScope.drawFretButton(cache: StageCache, center: Offset, radius: Float, lane: Int, pressed: Boolean) {
    if (!(radius > 0f)) return
    val color = LaneColors.forLane(lane)
    withTransform({
        translate(center.x, center.y)
        scale(radius, radius, Offset.Zero)
    }) {
        if (pressed) oval(color.copy(alpha = 0.35f), Offset.Zero, 1.35f, TILT * 1.6f)
        val style = cache.look.frets
        if (style == FretStyle.DISCS) {
            oval(Color.Black, Offset(0f, TILT * 0.35f), 1f, TILT)
            oval(cache.chrome, Offset.Zero, 1f, TILT)
            oval(color, Offset.Zero, 0.82f, TILT * 0.82f)
            oval(cache.fretInner(lane, pressed), Offset.Zero, FRET_INNER, TILT * FRET_INNER)
        } else {
            drawStyledButton(cache, style, color, pressed)
        }
    }
}

/** Rastro da nota longa. Brilha enquanto está sendo segurada. */
fun DrawScope.drawSustainTrail(start: Offset, end: Offset, width: Float, color: Color, active: Boolean) {
    if (active) drawLine(color.copy(alpha = 0.3f), start, end, width * 2.2f, StrokeCap.Round)
    drawLine(color.copy(alpha = if (active) 1f else 0.6f), start, end, width, StrokeCap.Round)
}

/** Largura, altura e deslocamento de cada camada da chama, em frações da largura e da altura. */
private val FLAME_LAYER_SHAPE = floatArrayOf(1f, 1f, 0f, 0.55f, 0.7f, 0f, 0.3f, 0.4f, -0.15f)

/** Faíscas: pares (deslocamento lateral, altura relativa de partida). */
private val SPARKS = floatArrayOf(-0.45f, 0.75f, 0.1f, 0.95f, 0.4f, 0.62f)

/** Chama do acerto saindo de [base] (centro do botão), no quadro dado por [progress] (0 a 1). */
fun DrawScope.drawFlame(cache: StageCache, base: Offset, width: Float, height: Float, progress: Float) {
    val alpha = flameAlpha(progress)
    // Sem tamanho (Canvas ainda sem medida) ou NaN: o contorno divide pela altura e viraria infinito.
    if (alpha <= 0f || !(width > 0f) || !(height > 0f)) return
    val h = height * flameScaleY(progress)
    for (layer in 0 until FLAME_LAYERS) {
        val layerWidth = width * FLAME_LAYER_SHAPE[layer * 3]
        val layerHeight = h * FLAME_LAYER_SHAPE[layer * 3 + 1]
        val path = cache.flamePaths[layer]
        path.flameTongue(base.x + width * FLAME_LAYER_SHAPE[layer * 3 + 2], layerWidth, layerHeight)
        withTransform({
            translate(0f, base.y)
            scale(1f, layerHeight, Offset.Zero)
        }) {
            drawPath(path, cache.flameBrushes[layer], alpha = alpha)
        }
    }
    for (i in SPARKS.indices step 2) {
        val spark = Offset(
            base.x + SPARKS[i] * width * (0.5f + progress),
            base.y - h * (SPARKS[i + 1] + 0.6f * progress),
        )
        drawCircle(FlameYellow, width * 0.05f, spark, alpha = alpha)
    }
}

/**
 * Língua de fogo com a base em y = 0 e a ponta em y = −1 (altura 1; x em px), desenhada com escala vertical
 * [height]: a mesma forma de antes, refeita no lugar (sem `Path` novo).
 */
private fun Path.flameTongue(centerX: Float, width: Float, height: Float) {
    rewind()
    val r = width / 2
    val rUp = r / height // o raio da base, medido em alturas
    moveTo(centerX, -1f)
    cubicTo(centerX + r * 0.2f, -0.65f, centerX + r, -0.5f, centerX + r, -rUp)
    cubicTo(centerX + r, -rUp * 0.45f, centerX + r * 0.55f, 0f, centerX, 0f)
    cubicTo(centerX - r * 0.55f, 0f, centerX - r, -rUp * 0.45f, centerX - r, -rUp)
    cubicTo(centerX - r, -0.5f, centerX - r * 0.2f, -0.65f, centerX, -1f)
    close()
}
