package com.rodrigomachado.riff.feature.calibration

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
abstract class CalibrationModule {
    @Binds
    abstract fun clickTrackPlayer(impl: AudioClickTrackPlayer): ClickTrackPlayer
}
