package com.rodrigomachado.riff.core.model

/** Estado do player do Spotify. [capturedAtElapsedMs] é o relógio monotônico do momento da leitura. */
data class PlaybackState(
    val trackUri: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val positionMs: Long,
    val isPaused: Boolean,
    val capturedAtElapsedMs: Long,
)
