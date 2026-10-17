package com.rodrigomachado.riff.core.data.queue

import com.rodrigomachado.riff.core.chart.match.TrackMatcher
import com.rodrigomachado.riff.core.chart.match.TrackQuery
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.encore.EncoreClient
import com.rodrigomachado.riff.core.encore.VersionTitles
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import kotlin.math.abs

/** O que a fila usa do Encore: a pesquisa e a olhada no começo do pacote. */
interface EncoreSearch {
    suspend fun search(query: String): List<EncoreChart>

    suspend fun hasSeparateGuitar(chart: EncoreChart): Boolean?
}

class EncoreClientSearch @Inject constructor(private val encore: EncoreClient) : EncoreSearch {
    override suspend fun search(query: String): List<EncoreChart> = encore.search(query, page = 1).charts

    override suspend fun hasSeparateGuitar(chart: EncoreChart): Boolean? = encore.hasSeparateGuitar(chart)
}

/** O que a pesquisa no Encore deu para uma faixa da fila. */
sealed interface QueuePick {
    /** Com a duração da faixa: vira casamento. */
    data class Exact(val chart: EncoreChart) : QueuePick

    /** Título e artista batem, a duração não: o de duração mais próxima, só como sugestão. */
    data class Closest(val chart: EncoreChart, val confidence: Float) : QueuePick

    data object NothingReliable : QueuePick

    /** Sem resposta do Encore (rede, fora do ar): vale tentar de novo. */
    data object SearchFailed : QueuePick
}

/**
 * O "melhor mapa" de uma faixa da fila (spec §5): só os com guitarra e semelhança de mapa encontrado (0,92),
 * originais antes de versões; desempate pela nota, pela guitarra separada e por mais dificuldades. Nenhum com a
 * duração da faixa: o de duração mais próxima (até 1 minuto), que a fila só baixa como sugestão.
 */
class QueueChartPicker @Inject constructor(private val encore: EncoreSearch) {
    /** Só o mapa com a duração da faixa; sem ele, nenhum. */
    suspend fun pick(track: TrackQuery): EncoreChart? = (choose(track) as? QueuePick.Exact)?.chart

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // a falha vira SearchFailed
    suspend fun choose(track: TrackQuery): QueuePick {
        val found = try {
            encore.search("${track.artist} ${track.title}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return QueuePick.SearchFailed
        }
        val withGuitar = found.filter { it.guitarDifficulties.isNotEmpty() }
        exact(track, withGuitar.filter { durationAgrees(track, it) })?.let { return QueuePick.Exact(it) }
        return closest(track, withGuitar) ?: QueuePick.NothingReliable
    }

    private suspend fun exact(track: TrackQuery, charts: List<EncoreChart>): EncoreChart? {
        val ranked = ranked(track, charts).sortedWith(
            compareByDescending<Candidate> { it.original }.thenByDescending { it.score },
        )
        val best = ranked.firstOrNull() ?: return null
        // Desempate só entre os empatados com o melhor; olhar o pacote custa uma ida ao Encore, então no máximo 3.
        val tied = ranked.filter { it.original == best.original && it.score == best.score }.take(MAX_PEEKS)
        if (tied.size == 1) return best.chart
        return tied
            .map { it to (encore.hasSeparateGuitar(it.chart) == true) }
            .sortedWith(
                compareByDescending<Pair<Candidate, Boolean>> { it.second }
                    .thenByDescending { it.first.chart.guitarDifficulties.size },
            )
            .first().first.chart
    }

    /** A semelhança sem contar a duração (é ela que não bate); originais primeiro, depois a menor diferença. */
    private fun closest(track: TrackQuery, charts: List<EncoreChart>): QueuePick.Closest? {
        val wanted = track.durationMs ?: return null
        val near = charts.filter { chart -> chart.durationMs?.let { abs(it - wanted) <= CLOSEST_LIMIT_MS } == true }
        val best = ranked(track.copy(durationMs = null), near).minWithOrNull(
            compareByDescending<Candidate> { it.original }
                .thenBy { candidate -> candidate.chart.durationMs?.let { abs(it - wanted) } }
                .thenByDescending { it.score },
        ) ?: return null
        return QueuePick.Closest(best.chart, best.score)
    }

    private fun ranked(track: TrackQuery, charts: List<EncoreChart>): List<Candidate> {
        // A faixa do Spotify já é uma versão (ao vivo, remix...): então a versão vem primeiro.
        val trackIsVersion = VersionTitles.isVersion(track.title)
        return charts
            .map {
                Candidate(
                    it,
                    TrackMatcher.score(track, it.asChart()),
                    VersionTitles.isVersion(it.name) == trackIsVersion,
                )
            }
            .filter { it.score >= TrackMatcher.AUTOMATIC_THRESHOLD }
    }

    /** Sozinho, só casa com a duração conferida: sem ela, título e artista parecidos não bastam. */
    private fun durationAgrees(track: TrackQuery, chart: EncoreChart): Boolean {
        val wanted = track.durationMs ?: return true
        val found = chart.durationMs ?: return false
        return abs(wanted - found) <= DURATION_TOLERANCE_MS
    }

    private class Candidate(val chart: EncoreChart, val score: Float, val original: Boolean)

    private companion object {
        const val MAX_PEEKS = 3
        const val DURATION_TOLERANCE_MS = 5_000L

        /** Mais longe que isso já é outra versão da música (ao vivo, estendida), não o mesmo áudio. */
        const val CLOSEST_LIMIT_MS = 60_000L
    }
}

/** Para a nota de semelhança, que compara faixa e mapa. */
internal fun EncoreChart.asChart() =
    Chart(md5, name, artist, null, charter, durationMs, "", ChartFormat.CHART, guitarDifficulties)
