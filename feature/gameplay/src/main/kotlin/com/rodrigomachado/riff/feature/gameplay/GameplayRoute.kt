package com.rodrigomachado.riff.feature.gameplay

import kotlinx.serialization.Serializable

/** Partida. Com [spotifyTrackUri], a música vem do Spotify e [trackOffsetMs] é o ajuste fino desta música. */
@Serializable
data class GameplayRoute(
    val chartId: String,
    val difficulty: String,
    val spotifyTrackUri: String? = null,
    val trackOffsetMs: Int = 0,
)
