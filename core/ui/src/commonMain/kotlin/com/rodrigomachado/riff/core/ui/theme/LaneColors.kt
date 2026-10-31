package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.ui.graphics.Color

/** Cores das 5 trilhas (verde, vermelho, amarelo, azul, laranja). São fixas para o jogador memorizá-las. */
object LaneColors {
    val all: List<Color> = listOf(
        Color(0xFF2ECC71),
        Color(0xFFE74C3C),
        Color(0xFFF1C40F),
        Color(0xFF3498DB),
        Color(0xFFE67E22),
    )

    fun forLane(lane: Int): Color = all[lane]
}
