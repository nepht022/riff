package com.rodrigomachado.riff.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SeparationQueueDao {
    @Query("SELECT * FROM separation_queue ORDER BY position")
    abstract fun observe(): Flow<List<SeparationQueueEntity>>

    @Query("SELECT * FROM separation_queue ORDER BY position LIMIT 1")
    abstract suspend fun first(): SeparationQueueEntity?

    /**
     * Põe no fim da fila, na ordem dada; a música que já está na fila mantém o lugar e a que saiu da biblioteca é
     * deixada de fora (o IGNORE do SQLite não vale para a chave estrangeira).
     */
    @Transaction
    open suspend fun append(items: List<Pair<String, String>>) {
        val existing = existingCharts(items.map { it.first }).toSet()
        val last = lastPosition() ?: 0
        insertIgnore(
            items.filter { it.first in existing }
                .mapIndexed { i, (chartId, title) -> SeparationQueueEntity(chartId, title, last + 1 + i) },
        )
    }

    @Query("SELECT id FROM charts WHERE id IN (:ids)")
    protected abstract suspend fun existingCharts(ids: List<String>): List<String>

    /** Conta mais uma tentativa de [chartId] e devolve quantas já são. */
    @Transaction
    open suspend fun startAttempt(chartId: String): Int {
        addAttempts(chartId, 1)
        return attemptsOf(chartId) ?: 0
    }

    /** Soma [delta] às tentativas de [chartId], sem ficar abaixo de zero (-1: o Riff do PC fechado no meio dela). */
    @Query("UPDATE separation_queue SET attempts = MAX(attempts + :delta, 0) WHERE chartId = :chartId")
    abstract suspend fun addAttempts(chartId: String, delta: Int)

    @Query("SELECT attempts FROM separation_queue WHERE chartId = :chartId")
    protected abstract suspend fun attemptsOf(chartId: String): Int?

    @Query("SELECT MAX(position) FROM separation_queue")
    protected abstract suspend fun lastPosition(): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertIgnore(entities: List<SeparationQueueEntity>)

    @Query("DELETE FROM separation_queue WHERE chartId = :chartId")
    abstract suspend fun delete(chartId: String)

    @Query("DELETE FROM separation_queue")
    abstract suspend fun clear()
}
