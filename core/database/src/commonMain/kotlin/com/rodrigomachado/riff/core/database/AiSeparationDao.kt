package com.rodrigomachado.riff.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class AiSeparationUsageRow(val songs: Int, val bytes: Long)

@Dao
interface AiSeparationDao {
    @Upsert
    suspend fun upsert(entity: AiSeparationEntity)

    @Query("SELECT state FROM ai_separations WHERE chartId = :chartId")
    suspend fun stateOf(chartId: String): String?

    @Query("SELECT chartId FROM ai_separations WHERE state = :state")
    fun observeIds(state: String): Flow<List<String>>

    @Query("SELECT chartId FROM ai_separations WHERE state = :state")
    suspend fun idsWith(state: String): List<String>

    @Query("SELECT COUNT(*) AS songs, COALESCE(SUM(bytes), 0) AS bytes FROM ai_separations WHERE state = 'SEPARATED'")
    fun observeUsage(): Flow<AiSeparationUsageRow>

    @Query("DELETE FROM ai_separations WHERE state = :state")
    suspend fun deleteState(state: String)
}
