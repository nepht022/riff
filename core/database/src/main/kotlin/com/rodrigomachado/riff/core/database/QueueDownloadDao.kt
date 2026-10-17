package com.rodrigomachado.riff.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface QueueDownloadDao {
    @Upsert
    suspend fun upsert(entity: QueueDownloadEntity)

    @Query("UPDATE queue_downloads SET played = 1 WHERE chartId = :chartId")
    suspend fun markPlayed(chartId: String)

    @Query("SELECT * FROM queue_downloads WHERE played = 0 ORDER BY downloadedAtEpochMs")
    suspend fun unplayed(): List<QueueDownloadEntity>

    @Query("SELECT * FROM queue_downloads WHERE trackUri = :trackUri")
    suspend fun forTrack(trackUri: String): QueueDownloadEntity?

    @Query("DELETE FROM queue_downloads WHERE chartId IN (:chartIds)")
    suspend fun delete(chartIds: List<String>)
}
