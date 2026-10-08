package com.rodrigomachado.riff.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ChartEntity::class, ChartDifficultyEntity::class, TrackMatchEntity::class, ScoreEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class RiffDatabase : RoomDatabase() {
    abstract fun chartDao(): ChartDao

    abstract fun trackMatchDao(): TrackMatchDao

    abstract fun scoreDao(): ScoreDao
}
