package com.rodrigomachado.riff.feature.gameplay

import android.os.SystemClock
import com.rodrigomachado.riff.core.game.TimeSource
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
