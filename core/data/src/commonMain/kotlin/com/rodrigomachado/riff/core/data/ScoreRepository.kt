package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.data.ranking.storedTable
import com.rodrigomachado.riff.core.database.ScoreDao
import com.rodrigomachado.riff.core.database.ScoreEntity
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.ScoreRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface ScoreRepository {
    /** Grava e devolve o id gerado (o `id` do [record] é ignorado). */
    suspend fun save(record: ScoreRecord): Long

    suspend fun get(id: Long): ScoreRecord?

    suspend fun best(chartId: String, difficulty: Difficulty): ScoreRecord?

    /** A última partida jogada (o "Continuar" da tela inicial); null sem partidas. */
    suspend fun last(): ScoreRecord?

    /** Os recordes de hoje (um por mapa e dificuldade), do jogado por último ao mais antigo, até [limit]. */
    suspend fun recentBests(limit: Int): List<ScoreRecord>

    /** O maior recorde de cada mapa, em qualquer dificuldade (um por mapa jogado), acompanhando o banco. */
    fun observeChartBests(): Flow<List<ScoreRecord>>
}

class RoomScoreRepository @Inject constructor(private val dao: ScoreDao) : ScoreRepository {
    override suspend fun save(record: ScoreRecord): Long = dao.insert(record.toEntity())

    override suspend fun get(id: Long): ScoreRecord? = dao.get(id)?.toModel()

    override suspend fun best(chartId: String, difficulty: Difficulty): ScoreRecord? = dao.best(
        chartId,
        difficulty.name,
    )?.toModel()

    override suspend fun last(): ScoreRecord? = dao.last()?.toModel()

    override suspend fun recentBests(limit: Int): List<ScoreRecord> = dao.recentBests(limit).mapNotNull { it.toModel() }

    override fun observeChartBests(): Flow<List<ScoreRecord>> =
        dao.observeChartBests().map { rows -> rows.mapNotNull { it.toModel() } }
}

private fun ScoreRecord.toEntity() = ScoreEntity(
    0, chartId, difficulty.name, score, accuracy, maxCombo, perfect, good, miss, playedAtEpochMs, cleanStreakMs,
    rankingTable.wire,
)

internal fun ScoreEntity.toModel(): ScoreRecord? {
    val level = Difficulty.entries.firstOrNull { it.name == difficulty } ?: return null
    return ScoreRecord(
        id, chartId, level, score, accuracy, maxCombo, perfect, good, miss, playedAtEpochMs, cleanStreakMs,
        storedTable(rankingTable),
    )
}
