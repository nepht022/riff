package com.rodrigomachado.riff.core.model

/** Ligação entre uma faixa do Spotify e um mapa. [trackOffsetMs] é o ajuste fino de sincronia desta música. */
data class TrackMatch(
    val spotifyTrackUri: String,
    val chartId: String,
    val confidence: Float,
    val isManual: Boolean,
    val trackOffsetMs: Int,
)
