package com.rodrigomachado.riff.core.model

/** Como a faixa do Spotify casa com a biblioteca (spec §5). */
sealed interface MatchStatus {
    /** Casamento automático (confiança alta) ou salvo pelo usuário. */
    data class Matched(val chart: Chart, val trackOffsetMs: Int) : MatchStatus

    /** Confiança média: o app sugere e pede confirmação. */
    data class Suggested(val chart: Chart, val confidence: Float) : MatchStatus

    data object NoMatch : MatchStatus
}

data class NowPlaying(val playback: PlaybackState, val match: MatchStatus)
