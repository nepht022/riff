package com.rodrigomachado.riff.feature.results

import kotlinx.serialization.Serializable

/**
 * Resultado de uma partida. [inRoom]: a partida foi de sala, e o Resultado só volta a ela (sem "Jogar de novo", que
 * abriria uma partida sozinho por cima da sala).
 */
@Serializable
data class ResultsRoute(val scoreId: Long, val inRoom: Boolean = false)
