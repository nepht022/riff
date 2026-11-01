package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.rodrigomachado.riff.core.model.FretStyle
import com.rodrigomachado.riff.core.model.NoteStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Designs de notas e botões além dos discos. Tudo em tamanho 1, já posicionado e escalado por quem chama, com as
 * formas prontas do StageCache: nada é criado por quadro. A cor da trilha é sempre a cor principal da peça.
 */

private val SHADOW = Color.Black.copy(alpha = 0.55f)
private val STEEL = Color(0xFFC8C8CD)
private val AMP_BLACK = Color(0xFF1E1E1E)
private val KNOB_TICK = Color(0xFFEADCBA)
private const val KNOB_TICKS = 11
private const val KNOB_START_DEG = 135.0
private const val KNOB_STEP_DEG = 27.0

/** Nota em pé sobre a trilha (de frente para o jogador), no design escolhido. */
internal fun DrawScope.drawStyledGem(
    cache: StageCache,
    style: NoteStyle,
    center: Offset,
    radius: Float,
    color: Color,
    hopo: Boolean,
) {
    withTransform({
        translate(center.x, center.y - radius * 0.4f)
        scale(radius * 0.85f, radius * 0.85f, Offset.Zero)
    }) {
        when (style) {
            NoteStyle.PICKS -> solid(cache.pickPath, color)
            NoteStyle.SKULLS -> skullGem(color)
            NoteStyle.STARS -> solid(cache.starPath, color, highlight = 0.45f)
            NoteStyle.BOLTS -> boltGem(cache, color)
            NoteStyle.FLAMES -> flameGem(cache, color)
            NoteStyle.DISCS -> Unit
        }
        if (hopo) drawCircle(Color.White.copy(alpha = 0.9f), 0.22f, Offset.Zero)
    }
}

/** Botão deitado na linha de acerto, achatado por [TILT]; pressionado, acende. */
internal fun DrawScope.drawStyledButton(cache: StageCache, style: FretStyle, color: Color, pressed: Boolean) {
    scale(1f, TILT, Offset.Zero) {
        when (style) {
            FretStyle.PEDALS -> pedal(color, pressed)
            FretStyle.KNOBS -> knob(color, pressed)
            FretStyle.SPEAKERS -> speaker(color, pressed)
            FretStyle.PICKS -> pickButton(cache, color, pressed)
            FretStyle.DISCS -> Unit
        }
    }
}

/** Forma com sombra deslocada, a cor e um brilho menor por cima. */
private fun DrawScope.solid(path: Path, color: Color, highlight: Float = 0.38f) {
    translate(0.06f, 0.1f) { drawPath(path, SHADOW) }
    drawPath(path, color)
    scale(
        highlight,
        highlight,
        Offset(0f, -0.1f),
    ) { drawPath(path, lerp(color, Color.White, 0.55f), alpha = color.alpha) }
}

private fun DrawScope.boltGem(cache: StageCache, color: Color) {
    translate(0.08f, 0.08f) { drawPath(cache.boltPath, SHADOW) }
    drawPath(cache.boltPath, color)
    scale(0.5f, 0.5f, Offset.Zero) { drawPath(cache.boltPath, lerp(color, Color.White, 0.5f), alpha = color.alpha) }
}

private fun DrawScope.flameGem(cache: StageCache, color: Color) {
    val base = Offset(0f, 1f)
    drawPath(cache.flamePath, lerp(color, Color.Black, 0.45f), alpha = color.alpha)
    scale(0.78f, 0.78f, base) { drawPath(cache.flamePath, color) }
    scale(0.45f, 0.45f, base) { drawPath(cache.flamePath, lerp(color, Color.White, 0.6f), alpha = color.alpha) }
}

private fun DrawScope.skullGem(color: Color) {
    val bone = lerp(color, Color.White, 0.15f)
    val dark = lerp(color, Color.Black, 0.75f)
    val a = color.alpha
    drawOval(SHADOW, Offset(-0.9f, -0.82f), Size(1.9f, 1.62f))
    drawOval(bone, Offset(-1f, -0.95f), Size(2f, 1.65f), alpha = a)
    drawRoundRect(bone, Offset(-0.55f, 0.35f), Size(1.1f, 0.6f), CornerRadius(0.15f), alpha = a)
    drawOval(dark, Offset(-0.67f, -0.1f), Size(0.54f, 0.47f), alpha = a)
    drawOval(dark, Offset(0.13f, -0.1f), Size(0.54f, 0.47f), alpha = a)
    for (dx in TEETH) drawLine(dark, Offset(dx, 0.7f), Offset(dx, 0.95f), 0.06f, alpha = a)
}

private val TEETH = floatArrayOf(-0.3f, -0.1f, 0.1f, 0.3f)

private fun DrawScope.pedal(color: Color, pressed: Boolean) {
    drawRoundRect(SHADOW, Offset(-0.9f, -0.75f), Size(1.8f, 1.8f), CornerRadius(0.2f))
    drawRoundRect(color, Offset(-0.9f, -0.9f), Size(1.8f, 1.8f), CornerRadius(0.2f))
    drawCircle(STEEL, 0.45f, Offset.Zero)
    drawCircle(if (pressed) Color.White else Color(0xFFEFEFF3), 0.3f, Offset.Zero)
    drawCircle(if (pressed) Color(0xFFFF3030) else Color(0xFF5A1010), 0.11f, Offset(0.6f, -0.68f))
}

private fun DrawScope.knob(color: Color, pressed: Boolean) {
    for (k in 0 until KNOB_TICKS) {
        val a = (KNOB_START_DEG + k * KNOB_STEP_DEG) * PI / 180.0
        val c = cos(a).toFloat()
        val s = sin(a).toFloat()
        drawLine(KNOB_TICK, Offset(c * 0.82f, s * 0.82f), Offset(c, s), 0.06f)
    }
    drawCircle(AMP_BLACK, 0.7f, Offset.Zero)
    drawCircle(if (pressed) lerp(color, Color.White, 0.35f) else color, 0.56f, Offset.Zero)
    drawLine(Color.White, Offset.Zero, Offset(0.42f, -0.3f), 0.09f)
}

private fun DrawScope.speaker(color: Color, pressed: Boolean) {
    drawCircle(Color(0xFF282828), 1f, Offset.Zero)
    drawCircle(lerp(color, Color.Black, if (pressed) 0.1f else 0.45f), 0.85f, Offset.Zero)
    drawCircle(color, 0.65f, Offset.Zero, style = SPEAKER_RING)
    drawCircle(color, 0.45f, Offset.Zero, style = SPEAKER_RING)
    drawCircle(if (pressed) Color.White else color, 0.22f, Offset.Zero)
}

private val SPEAKER_RING = Stroke(0.06f)

private fun DrawScope.pickButton(cache: StageCache, color: Color, pressed: Boolean) {
    drawPath(cache.pickPath, Color(0xFFE6E6E6))
    scale(0.8f, 0.8f, Offset.Zero) { drawPath(cache.pickPath, color) }
    scale(0.42f, 0.42f, Offset.Zero) { drawPath(cache.pickPath, if (pressed) Color.White else Color(0xFF0F0F0F)) }
}

/** Formas em tamanho 1 de [RockShapes], cada uma num `Path` novo. */
internal object UnitShapes {
    fun pick() = Path().also { RockShapes.pick(it) }

    fun star() = Path().also { RockShapes.star(it) }

    fun bolt() = Path().also { RockShapes.bolt(it) }

    fun flame() = Path().also { RockShapes.flame(it) }
}
