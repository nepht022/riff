package com.rodrigomachado.riff.core.chart.match

import com.rodrigomachado.riff.core.model.Chart
import kotlin.math.abs

/** Faixa do Spotify a casar com um mapa. */
data class TrackQuery(val title: String, val artist: String, val durationMs: Long?)

enum class MatchKind { AUTOMATIC, NEEDS_CONFIRMATION }

data class MatchCandidate(val chart: Chart, val confidence: Float, val kind: MatchKind)

/** Casamento entre faixa e mapa (spec §5). */
object TrackMatcher {
    const val AUTOMATIC_THRESHOLD = 0.92f
    const val SUGGESTION_THRESHOLD = 0.75f
    private const val TITLE_WEIGHT = 0.6
    private const val ARTIST_WEIGHT = 0.4
    private const val DURATION_PENALTY = 0.1
    private const val DURATION_TOLERANCE_MS = 5_000

    fun score(query: TrackQuery, chart: Chart): Float {
        val title = JaroWinkler.similarity(TextNormalizer.normalize(query.title), TextNormalizer.normalize(chart.title))
        val artist = JaroWinkler.similarity(
            TextNormalizer.normalize(query.artist),
            TextNormalizer.normalize(chart.artist),
        )
        var score = TITLE_WEIGHT * title + ARTIST_WEIGHT * artist
        val queryDuration = query.durationMs
        val chartDuration = chart.durationMs
        if (queryDuration != null && chartDuration != null && abs(
                queryDuration - chartDuration,
            ) > DURATION_TOLERANCE_MS
        ) {
            score -= DURATION_PENALTY
        }
        return score.coerceIn(0.0, 1.0).toFloat()
    }

    fun bestMatch(query: TrackQuery, charts: List<Chart>): MatchCandidate? {
        val (chart, confidence) = charts.map { it to score(query, it) }.maxByOrNull { it.second } ?: return null
        if (confidence < SUGGESTION_THRESHOLD) return null
        val kind = if (confidence >= AUTOMATIC_THRESHOLD) MatchKind.AUTOMATIC else MatchKind.NEEDS_CONFIRMATION
        return MatchCandidate(chart, confidence, kind)
    }
}
