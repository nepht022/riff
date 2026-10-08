package com.rodrigomachado.riff.feature.results

import kotlinx.serialization.Serializable

/** Resultado. [spotifyTrackUri] e [trackOffsetMs] fazem "Jogar de novo" continuar no modo Spotify. */
@Serializable
data class ResultsRoute(val scoreId: Long, val spotifyTrackUri: String? = null, val trackOffsetMs: Int = 0)
