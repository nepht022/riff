package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.chart.match.MatchKind
import com.rodrigomachado.riff.core.chart.match.TrackMatcher
import com.rodrigomachado.riff.core.chart.match.TrackQuery
import com.rodrigomachado.riff.core.database.TrackMatchDao
import com.rodrigomachado.riff.core.database.TrackMatchEntity
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

interface TrackMatchRepository {
    /** Muda a cada [save], para quem mostra o casamento recalcular. */
    val changes: StateFlow<Int>

    /** Casamento salvo (prioridade) ou automático (spec §5). */
    suspend fun matchFor(track: PlaybackState): MatchStatus

    /** Grava a confirmação ou a escolha manual; mantém o ajuste fino se o mapa for o mesmo. */
    suspend fun save(spotifyTrackUri: String, chartId: String, confidence: Float, isManual: Boolean)
}

@Singleton
class RoomTrackMatchRepository @Inject constructor(
    private val dao: TrackMatchDao,
    private val charts: ChartRepository,
) : TrackMatchRepository {
    private val version = MutableStateFlow(0)
    override val changes: StateFlow<Int> = version.asStateFlow()

    override suspend fun matchFor(track: PlaybackState): MatchStatus {
        val saved = dao.get(track.trackUri)
        val savedChart = saved?.let { charts.getChart(it.chartId) }
        if (saved != null && savedChart != null) return MatchStatus.Matched(savedChart, saved.trackOffsetMs)
        val query = TrackQuery(track.title, track.artist, track.durationMs)
        val candidate = TrackMatcher.bestMatch(query, charts.observeCharts().first()) ?: return MatchStatus.NoMatch
        return when (candidate.kind) {
            MatchKind.AUTOMATIC -> MatchStatus.Matched(candidate.chart, 0)
            MatchKind.NEEDS_CONFIRMATION -> MatchStatus.Suggested(candidate.chart, candidate.confidence)
        }
    }

    override suspend fun save(spotifyTrackUri: String, chartId: String, confidence: Float, isManual: Boolean) {
        val offset = dao.get(spotifyTrackUri)?.takeIf { it.chartId == chartId }?.trackOffsetMs ?: 0
        dao.upsert(TrackMatchEntity(spotifyTrackUri, chartId, confidence, isManual, offset))
        version.update { it + 1 }
    }
}
