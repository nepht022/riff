package com.rodrigomachado.riff.feature.gameplay

import kotlinx.serialization.Serializable

/**
 * Partida de um mapa da Biblioteca, na dificuldade escolhida. Com [roomCode], a partida da sala na rodada [round]:
 * começa junto com os outros e não pausa (ver [MatchStart]).
 */
@Serializable
data class GameplayRoute(val chartId: String, val difficulty: String, val roomCode: String? = null, val round: Int = 0)
