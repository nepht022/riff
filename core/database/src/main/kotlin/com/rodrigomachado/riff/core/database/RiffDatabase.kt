package com.rodrigomachado.riff.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChartEntity::class,
        ChartDifficultyEntity::class,
        TrackMatchEntity::class,
        ScoreEntity::class,
        ChartOffsetEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class RiffDatabase : RoomDatabase() {
    abstract fun chartDao(): ChartDao

    abstract fun trackMatchDao(): TrackMatchDao

    abstract fun scoreDao(): ScoreDao

    abstract fun chartOffsetDao(): ChartOffsetDao
}
