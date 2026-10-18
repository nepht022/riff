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

    /** O mesmo, para uma faixa da fila: a duração pode faltar (a sessão de mídia não traz). */
    suspend fun matchFor(trackUri: String, query: TrackQuery): MatchStatus =
        matchFor(PlaybackState(trackUri, query.title, query.artist, query.durationMs ?: 0, 0, true, 0))

    /** Grava a confirmação ou a escolha manual; mantém o ajuste fino se o mapa for o mesmo. */
    suspend fun save(spotifyTrackUri: String, chartId: String, confidence: Float, isManual: Boolean)

    /** Grava um mapa só como sugestão (a fila, com outra duração): confirmar depois o torna casamento. */
    suspend fun saveSuggestion(spotifyTrackUri: String, chartId: String, confidence: Float)

    /** Ajuste salvo desta faixa com este mapa; `null` se não houver casamento salvo com ele. */
    suspend fun offsetFor(spotifyTrackUri: String, chartId: String): Int?

    /** Grava o ajuste fino; um casamento automático passa a ficar salvo (com o mesmo mapa). */
    suspend fun saveOffset(spotifyTrackUri: String, chartId: String, offsetMs: Int)
}

@Singleton
class RoomTrackMatchRepository @Inject constructor(
    private val dao: TrackMatchDao,
    private val charts: ChartRepository,
) : TrackMatchRepository {
    private val version = MutableStateFlow(0)
    override val changes: StateFlow<Int> = version.asStateFlow()

    override suspend fun matchFor(track: PlaybackState): MatchStatus =
        matchFor(track.trackUri, TrackQuery(track.title, track.artist, track.durationMs))

    override suspend fun matchFor(trackUri: String, query: TrackQuery): MatchStatus {
        val saved = dao.get(trackUri)
        val savedChart = saved?.let { charts.getChart(it.chartId) }
        if (saved != null && savedChart != null) {
            return if (saved.suggested) {
                MatchStatus.Suggested(savedChart, saved.confidence)
            } else {
                MatchStatus.Matched(savedChart, saved.trackOffsetMs)
            }
        }
        val candidate = TrackMatcher.bestMatch(query, charts.observeCharts().first()) ?: return MatchStatus.NoMatch
        return when (candidate.kind) {
            MatchKind.AUTOMATIC -> MatchStatus.Matched(candidate.chart, 0)
            MatchKind.NEEDS_CONFIRMATION -> MatchStatus.Suggested(candidate.chart, candidate.confidence)
        }
    }

    override suspend fun save(spotifyTrackUri: String, chartId: String, confidence: Float, isManual: Boolean) {
        val same = dao.get(spotifyTrackUri)?.takeIf { it.chartId == chartId }
        dao.upsert(
            TrackMatchEntity(
                spotifyTrackUri,
                chartId,
                confidence,
                isManual,
                trackOffsetMs = same?.trackOffsetMs ?: 0,
                offsetChosen = same?.offsetChosen ?: false,
            ),
        )
        version.update { it + 1 }
    }

    override suspend fun saveSuggestion(spotifyTrackUri: String, chartId: String, confidence: Float) {
        dao.upsert(TrackMatchEntity(spotifyTrackUri, chartId, confidence, isManual = false, 0, suggested = true))
        version.update { it + 1 }
    }

    override suspend fun offsetFor(spotifyTrackUri: String, chartId: String): Int? =
        dao.get(spotifyTrackUri)?.takeIf { it.chartId == chartId && it.offsetChosen }?.trackOffsetMs?.limited()

    override suspend fun saveOffset(spotifyTrackUri: String, chartId: String, offsetMs: Int) {
        val saved = dao.get(spotifyTrackUri)?.takeIf { it.chartId == chartId }
        val updated = saved?.copy(trackOffsetMs = offsetMs.limited(), offsetChosen = true)
            ?: TrackMatchEntity(
                spotifyTrackUri,
                chartId,
                confidence = 1f,
                isManual = false,
                trackOffsetMs = offsetMs.limited(),
                offsetChosen = true,
            )
        ignoringRemovedChart { dao.upsert(updated) }
        version.update { it + 1 }
    }
}
