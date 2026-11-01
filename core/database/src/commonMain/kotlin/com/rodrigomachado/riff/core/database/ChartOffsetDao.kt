package com.rodrigomachado.riff.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ChartOffsetDao {
    @Query("SELECT offsetMs FROM chart_offsets WHERE chartId = :chartId")
    suspend fun get(chartId: String): Int?

    @Upsert
    suspend fun upsert(offset: ChartOffsetEntity)
}
