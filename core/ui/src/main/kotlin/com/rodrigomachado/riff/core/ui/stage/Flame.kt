package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.util.lerp

/** Duração da chama de acerto. */
const val FLAME_DURATION_MS = 250

/** Quadro da chama: opacidade e altura relativa. */
data class FlameFrame(val alpha: Float, val scaleY: Float)

private val NOTHING = FlameFrame(alpha = 0f, scaleY = 0f)

private val KEYFRAMES = listOf(
    0f to FlameFrame(alpha = 0f, scaleY = 0.2f),
    0.15f to FlameFrame(alpha = 1f, scaleY = 1.15f),
    0.5f to FlameFrame(alpha = 0.9f, scaleY = 0.95f),
    1f to FlameFrame(alpha = 0f, scaleY = 0.6f),
)

/** Quadro da chama em [progress] (0 a 1 ao longo de [FLAME_DURATION_MS]). Fora disso, ou NaN, não desenha nada. */
fun flameFrame(progress: Float): FlameFrame {
    if (progress !in 0f..1f) return NOTHING
    val index = KEYFRAMES.indexOfLast { it.first <= progress }.coerceAtMost(KEYFRAMES.lastIndex - 1)
    val (start, from) = KEYFRAMES[index]
    val (end, to) = KEYFRAMES[index + 1]
    val t = (progress - start) / (end - start)
    return FlameFrame(alpha = lerp(from.alpha, to.alpha, t), scaleY = lerp(from.scaleY, to.scaleY, t))
}
