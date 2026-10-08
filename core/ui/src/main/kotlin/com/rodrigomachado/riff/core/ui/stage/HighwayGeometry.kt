package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.util.lerp

private const val BOTTOM_WIDTH_FRACTION = 0.94f

/**
 * Geometria da pista em trapézio: estreita no fundo (depth 0) e larga na base da tela (depth 1).
 * As trilhas dividem a largura por igual em qualquer profundidade.
 */
class HighwayGeometry(val size: Size, val laneCount: Int = 5, private val topWidthFraction: Float = 0.4f) {
    private val bottomWidth = size.width * BOTTOM_WIDTH_FRACTION
    private val topWidth = bottomWidth * topWidthFraction

    fun widthAt(depth: Float): Float = lerp(topWidth, bottomWidth, depth)

    fun leftAt(depth: Float): Float = (size.width - widthAt(depth)) / 2

    fun yAt(depth: Float): Float = depth * size.height

    fun laneCenter(lane: Int, depth: Float): Offset =
        Offset(leftAt(depth) + widthAt(depth) * (lane + 0.5f) / laneCount, yAt(depth))

    /** Fator de escala de gemas e botões na profundidade [depth] (1 na base da tela). */
    fun scaleAt(depth: Float): Float = widthAt(depth) / bottomWidth
}
