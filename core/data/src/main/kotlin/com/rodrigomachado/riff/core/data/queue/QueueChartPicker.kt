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

/** O que a fila usa do Encore: a pesquisa e a olhada no começo do pacote. */
interface EncoreSearch {
    suspend fun search(query: String): List<EncoreChart>

    suspend fun hasSeparateGuitar(chart: EncoreChart): Boolean?
}

class EncoreClientSearch @Inject constructor(private val encore: EncoreClient) : EncoreSearch {
    override suspend fun search(query: String): List<EncoreChart> = encore.search(query, page = 1).charts

    override suspend fun hasSeparateGuitar(chart: EncoreChart): Boolean? = encore.hasSeparateGuitar(chart)
}

/**
 * O "melhor mapa" de uma faixa da fila (spec §5): só os com guitarra e semelhança de mapa encontrado (0,92),
 * originais antes de versões; desempate pela nota, pela guitarra separada e por mais dificuldades. Nenhum
 * confiável, nenhum mapa: um casamento errado é pior que nenhum.
 */
class QueueChartPicker @Inject constructor(private val encore: EncoreSearch) {
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // sem resultado, a faixa só fica sem mapa
    suspend fun pick(track: TrackQuery): EncoreChart? {
        val found = try {
            encore.search("${track.artist} ${track.title}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        val ranked = found
            .filter { it.guitarDifficulties.isNotEmpty() }
            .map { Candidate(it, TrackMatcher.score(track, it.asChart()), !VersionTitles.isVersion(it.name)) }
            .filter { it.score >= TrackMatcher.AUTOMATIC_THRESHOLD }
            .sortedWith(compareByDescending<Candidate> { it.original }.thenByDescending { it.score })
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

    private class Candidate(val chart: EncoreChart, val score: Float, val original: Boolean)

    private companion object {
        const val MAX_PEEKS = 3
    }
}

/** Para a nota de semelhança, que compara faixa e mapa. */
internal fun EncoreChart.asChart() =
    Chart(md5, name, artist, null, charter, durationMs, "", ChartFormat.CHART, guitarDifficulties)
