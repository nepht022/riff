package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/** Razão de contraste WCAG 2.x entre duas cores opacas, de 1 (igual) a 21 (branco e preto). */
fun contrastRatio(foreground: Color, background: Color): Float {
    val a = foreground.luminance()
    val b = background.luminance()
    return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
}
