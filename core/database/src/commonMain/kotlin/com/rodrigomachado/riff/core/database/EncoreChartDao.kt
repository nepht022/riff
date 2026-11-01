package com.rodrigomachado.riff.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EncoreChartDao {
    @Upsert
    suspend fun upsert(entity: EncoreChartEntity)

    @Query("SELECT md5 FROM encore_charts")
    fun observeMd5s(): Flow<List<String>>

    @Query("SELECT chartId FROM encore_charts WHERE md5 = :md5")
    suspend fun chartIdOf(md5: String): String?
}
