package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * Pista em perspectiva, como vista por uma câmera atrás da linha de acerto.
 *
 * As posições são dadas em **distância** à frente da linha de acerto (0 = na linha; positivo = mais longe;
 * negativo = abaixo dela). A escala cai com `câmera / (câmera + distância)`: notas igualmente espaçadas
 * no tempo nascem pequenas e juntas no fundo e crescem e se espaçam ao chegar.
 */
class HighwayGeometry(
    val size: Size,
    val laneCount: Int = 5,
    val cameraDistance: Float = 2.2f,
    val visibleDistance: Float = 6f,
    strikeYFraction: Float = 0.82f,
    strikeWidthFraction: Float = 0.92f,
    horizonYFraction: Float = 0.02f,
) {
    init {
        require(laneCount > 0) { "laneCount precisa ser positivo: $laneCount" }
        require(cameraDistance > 0f) { "cameraDistance precisa ser positiva: $cameraDistance" }
        require(visibleDistance > 0f) { "visibleDistance precisa ser positiva: $visibleDistance" }
    }

    private val strikeY = size.height * strikeYFraction
    private val horizonY = size.height * horizonYFraction
    private val strikeWidth = size.width * strikeWidthFraction

    /** Borda de baixo da pista, um pouco abaixo da linha de acerto. */
    val nearDistance: Float = -0.1f * cameraDistance

    /** Largura de uma trilha na linha de acerto: referência para o tamanho de gemas e alvos. */
    val laneWidthAtStrike: Float = strikeWidth / laneCount

    fun scaleAt(distance: Float): Float = cameraDistance / (cameraDistance + distance)

    fun yAt(distance: Float): Float = horizonY + (strikeY - horizonY) * scaleAt(distance)

    fun widthAt(distance: Float): Float = strikeWidth * scaleAt(distance)

    fun leftAt(distance: Float): Float = (size.width - widthAt(distance)) / 2

    fun laneCenter(lane: Int, distance: Float): Offset =
        Offset(leftAt(distance) + widthAt(distance) * (lane + 0.5f) / laneCount, yAt(distance))

    /** Trilha (na tela) sob o [x] na linha de acerto; fora da pista vale a trilha mais próxima. */
    fun laneAt(x: Float): Int {
        val fraction = (x - leftAt(0f)) / widthAt(0f)
        return (fraction * laneCount).toInt().coerceIn(0, laneCount - 1)
    }
}
