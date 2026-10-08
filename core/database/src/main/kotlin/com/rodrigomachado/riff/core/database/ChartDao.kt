package com.rodrigomachado.riff.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ChartDao {
    @Transaction
    @Query("SELECT * FROM charts ORDER BY artist COLLATE NOCASE, title COLLATE NOCASE")
    abstract fun observeAll(): Flow<List<ChartWithDifficulties>>

    /** Grava o mapa e substitui suas dificuldades, numa transação. */
    @Transaction
    open suspend fun replace(chart: ChartEntity, difficulties: List<ChartDifficultyEntity>) {
        upsert(chart)
        deleteDifficulties(chart.id)
        insertDifficulties(difficulties)
    }

    @Query("DELETE FROM charts WHERE lastSeenScanId != :scanId")
    abstract suspend fun deleteNotSeenIn(scanId: Long)

    @Upsert
    abstract suspend fun upsert(chart: ChartEntity)

    @Query("DELETE FROM chart_difficulties WHERE chartId = :chartId")
    abstract suspend fun deleteDifficulties(chartId: String)

    @Insert
    abstract suspend fun insertDifficulties(difficulties: List<ChartDifficultyEntity>)
}
