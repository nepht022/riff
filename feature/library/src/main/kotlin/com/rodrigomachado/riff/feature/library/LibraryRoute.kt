package com.rodrigomachado.riff.feature.library

import kotlinx.serialization.Serializable

/** Biblioteca. Com [matchTrackUri], serve para escolher o mapa da música que está tocando no Spotify. */
@Serializable
data class LibraryRoute(val matchTrackUri: String? = null, val matchTrackTitle: String? = null)

/** Música do Spotify esperando o jogador escolher o mapa. */
data class TrackToMatch(val trackUri: String, val title: String)
