package com.rodrigomachado.riff.core.database

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [
        ChartEntity::class,
        ChartDifficultyEntity::class,
        ScoreEntity::class,
        ChartOffsetEntity::class,
        EncoreChartEntity::class,
        AiSeparationEntity::class,
        RankingPendingEntity::class,
        SeparationQueueEntity::class,
    ],
    version = 14,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = MarkChosenOffsets::class),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13, spec = DropSpotifyTables::class),
        AutoMigration(from = 13, to = 14),
    ],
)
@ConstructedBy(RiffDatabaseConstructor::class)
abstract class RiffDatabase : RoomDatabase() {
    abstract fun chartDao(): ChartDao

    abstract fun scoreDao(): ScoreDao

    abstract fun chartOffsetDao(): ChartOffsetDao

    abstract fun encoreChartDao(): EncoreChartDao

    abstract fun aiSeparationDao(): AiSeparationDao

    abstract fun rankingPendingDao(): RankingPendingDao

    abstract fun separationQueueDao(): SeparationQueueDao
}

/** O Room gera a implementação para cada plataforma (celular e PC). */
@Suppress("KotlinNoActualForExpect")
expect object RiffDatabaseConstructor : RoomDatabaseConstructor<RiffDatabase> {
    override fun initialize(): RiffDatabase
}

/** Na versão 2, um ajuste diferente de 0 só existia se alguém o escolheu: continua valendo. */
class MarkChosenOffsets : AutoMigrationSpec {
    override fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL("UPDATE track_matches SET offsetChosen = 1 WHERE trackOffsetMs != 0")
    }
}

/** Na versão 13 o Spotify saiu do app: os casamentos com as faixas e o registro da fila de mapas vão junto. */
@DeleteTable(tableName = "track_matches")
@DeleteTable(tableName = "queue_downloads")
class DropSpotifyTables : AutoMigrationSpec
