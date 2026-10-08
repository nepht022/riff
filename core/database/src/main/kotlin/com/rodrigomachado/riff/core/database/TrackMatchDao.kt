package com.rodrigomachado.riff.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface TrackMatchDao {
    @Upsert
    suspend fun upsert(match: TrackMatchEntity)

    @Query("SELECT * FROM track_matches WHERE spotifyTrackUri = :spotifyTrackUri")
    suspend fun get(spotifyTrackUri: String): TrackMatchEntity?
}
