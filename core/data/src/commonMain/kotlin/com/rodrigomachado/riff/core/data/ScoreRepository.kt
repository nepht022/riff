package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.data.ranking.storedTable
import com.rodrigomachado.riff.core.database.ScoreDao
import com.rodrigomachado.riff.core.database.ScoreEntity
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.ScoreRecord
import javax.inject.Inject

interface ScoreRepository {
    /** Grava e devolve o id gerado (o `id` do [record] é ignorado). */
    suspend fun save(record: ScoreRecord): Long

    suspend fun get(id: Long): ScoreRecord?

    suspend fun best(chartId: String, difficulty: Difficulty): ScoreRecord?
}

class RoomScoreRepository @Inject constructor(private val dao: ScoreDao) : ScoreRepository {
    override suspend fun save(record: ScoreRecord): Long = dao.insert(record.toEntity())

    override suspend fun get(id: Long): ScoreRecord? = dao.get(id)?.toModel()

    override suspend fun best(chartId: String, difficulty: Difficulty): ScoreRecord? = dao.best(
        chartId,
        difficulty.name,
    )?.toModel()
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
