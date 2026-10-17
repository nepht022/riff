package com.rodrigomachado.riff.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [
        ChartEntity::class,
        ChartDifficultyEntity::class,
        TrackMatchEntity::class,
        ScoreEntity::class,
        ChartOffsetEntity::class,
        EncoreChartEntity::class,
        AiSeparationEntity::class,
        QueueDownloadEntity::class,
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = MarkChosenOffsets::class),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
)
abstract class RiffDatabase : RoomDatabase() {
    abstract fun chartDao(): ChartDao

    abstract fun trackMatchDao(): TrackMatchDao

    abstract fun scoreDao(): ScoreDao

    abstract fun chartOffsetDao(): ChartOffsetDao

    abstract fun encoreChartDao(): EncoreChartDao

    abstract fun queueDownloadDao(): QueueDownloadDao

    abstract fun aiSeparationDao(): AiSeparationDao
}

/** Na versão 2, um ajuste diferente de 0 só existia se alguém o escolheu: continua valendo. */
class MarkChosenOffsets : AutoMigrationSpec {
    override fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL("UPDATE track_matches SET offsetChosen = 1 WHERE trackOffsetMs != 0")
    }
}
