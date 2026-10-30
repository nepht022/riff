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

    /** Põe no fim da fila, na ordem dada; a música que já está na fila mantém o lugar. */
    @Transaction
    open suspend fun append(items: List<Pair<String, String>>) {
        val last = lastPosition() ?: 0
        insertIgnore(items.mapIndexed { i, (chartId, title) -> SeparationQueueEntity(chartId, title, last + 1 + i) })
    }

    @Query("SELECT MAX(position) FROM separation_queue")
    protected abstract suspend fun lastPosition(): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertIgnore(entities: List<SeparationQueueEntity>)

    @Query("DELETE FROM separation_queue WHERE chartId = :chartId")
    abstract suspend fun delete(chartId: String)

    @Query("DELETE FROM separation_queue")
    abstract suspend fun clear()
}
