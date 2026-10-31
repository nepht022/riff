package com.rodrigomachado.riff.feature.gameplay

import kotlinx.serialization.Serializable

/** Partida de um mapa da Biblioteca, na dificuldade escolhida. */
@Serializable
data class GameplayRoute(val chartId: String, val difficulty: String)
