package com.rodrigomachado.riff.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): RiffDatabase =
        Room.databaseBuilder(context, RiffDatabase::class.java, "riff.db").build()

    @Provides
    fun chartDao(database: RiffDatabase): ChartDao = database.chartDao()

    @Provides
    fun scoreDao(database: RiffDatabase): ScoreDao = database.scoreDao()

    @Provides
    fun chartOffsetDao(database: RiffDatabase): ChartOffsetDao = database.chartOffsetDao()

    @Provides
    fun encoreChartDao(database: RiffDatabase): EncoreChartDao = database.encoreChartDao()

    @Provides
    fun aiSeparationDao(database: RiffDatabase): AiSeparationDao = database.aiSeparationDao()

    @Provides
    fun rankingPendingDao(database: RiffDatabase): RankingPendingDao = database.rankingPendingDao()

    @Provides
    fun separationQueueDao(database: RiffDatabase): SeparationQueueDao = database.separationQueueDao()
}
