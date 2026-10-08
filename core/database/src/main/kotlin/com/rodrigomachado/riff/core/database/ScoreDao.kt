package com.rodrigomachado.riff.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScoreDao {
    @Insert
    suspend fun insert(score: ScoreEntity): Long

    @Query("SELECT * FROM scores WHERE id = :id")
    suspend fun get(id: Long): ScoreEntity?

    @Query("SELECT * FROM scores WHERE chartId = :chartId AND difficulty = :difficulty ORDER BY score DESC LIMIT 1")
    suspend fun best(chartId: String, difficulty: String): ScoreEntity?
}
