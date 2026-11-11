package com.rodrigomachado.riff.feature.results

import kotlinx.serialization.Serializable

/**
 * Resultado de uma partida. Com [roomCode], a partida foi da sala [roomCode], rodada [round]: o Resultado só volta a
 * ela (sem "Jogar de novo", que abriria uma partida sozinho por cima da sala) e oferece "Ver a sala" (o Resultado da
 * sala, que o app abre com o código e a rodada: o Resultado não conhece a sala).
 */
@Serializable
data class ResultsRoute(val scoreId: Long, val roomCode: String? = null, val round: Int = 0) {
    /** A partida foi de sala. */
    val inRoom: Boolean get() = roomCode != null
}
