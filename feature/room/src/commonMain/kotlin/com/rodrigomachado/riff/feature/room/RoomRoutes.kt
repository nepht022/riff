package com.rodrigomachado.riff.feature.room

import kotlinx.serialization.Serializable

/** A Sala: "Criar sala" e "Entrar com código". */
@Serializable
data object RoomEntryRoute

/** Digitar o código de uma sala para entrar nela. */
@Serializable
data object RoomJoinRoute

/** A sala [code] ao vivo (o código, os jogadores e o Sair). */
@Serializable
data class RoomLobbyRoute(val code: String)

/**
 * O Resultado da sala [code], rodada [round]: a classificação de todos, aberta pelo "Ver a sala" do Resultado da
 * partida de sala.
 */
@Serializable
data class RoomResultsRoute(val code: String, val round: Int)
