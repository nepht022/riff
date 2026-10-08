package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.database.ChartDao
import com.rodrigomachado.riff.core.database.ChartDifficultyEntity
import com.rodrigomachado.riff.core.database.ChartEntity
import com.rodrigomachado.riff.core.database.ChartWithDifficulties
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomChartRepository @Inject constructor(private val dao: ChartDao) : ChartRepository {
    override fun observeCharts(): Flow<List<Chart>> = dao.observeAll().map { rows -> rows.mapNotNull { it.toModel() } }

    override suspend fun saveScanned(chart: Chart, scanId: Long) {
        dao.replace(chart.toEntity(scanId), chart.difficulties.map { ChartDifficultyEntity(chart.id, it.name) })
    }

    override suspend fun removeNotSeenIn(scanId: Long) = dao.deleteNotSeenIn(scanId)
}

private fun Chart.toEntity(scanId: Long) =
    ChartEntity(id, title, artist, album, charter, durationMs, folderUri, format.name, scanId)

/** Linha com formato desconhecido (versão futura do app) é ignorada em vez de quebrar a lista. */
private fun ChartWithDifficulties.toModel(): Chart? {
    val format = ChartFormat.entries.firstOrNull { it.name == chart.format } ?: return null
    return Chart(
        id = chart.id,
        title = chart.title,
        artist = chart.artist,
        album = chart.album,
        charter = chart.charter,
        durationMs = chart.durationMs,
        folderUri = chart.folderUri,
        format = format,
        difficulties = difficulties.mapNotNull { row ->
            Difficulty.entries.firstOrNull { it.name == row.difficulty }
        }.toSet(),
    )
}
