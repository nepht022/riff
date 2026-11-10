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
