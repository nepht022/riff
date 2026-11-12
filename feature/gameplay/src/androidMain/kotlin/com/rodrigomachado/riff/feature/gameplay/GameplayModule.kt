package com.rodrigomachado.riff.feature.gameplay

import android.os.SystemClock
import com.rodrigomachado.riff.core.game.TimeSource
import com.rodrigomachado.riff.core.ui.art.ChartArtImages
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object GameplayModule {
    @Provides
    fun timeSource(): TimeSource = TimeSource { SystemClock.elapsedRealtime() }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GameplayBindsModule {
    @Binds
    abstract fun chartArtImages(impl: DecodedChartArtImages): ChartArtImages
}
