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

    @Query("SELECT * FROM scores WHERE chartId = :chartId AND difficulty = :difficulty")
    suspend fun of(chartId: String, difficulty: String): List<ScoreEntity>

    @Query("SELECT * FROM scores")
    suspend fun all(): List<ScoreEntity>

    // Empate: vale o recorde mais antigo (igualar não é "NEW BEST").
    @Query(
        "SELECT * FROM scores WHERE chartId = :chartId AND difficulty = :difficulty " +
            "ORDER BY score DESC, id ASC LIMIT 1",
    )
    suspend fun best(chartId: String, difficulty: String): ScoreEntity?
}
