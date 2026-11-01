package com.rodrigomachado.riff.feature.calibration

import com.rodrigomachado.riff.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** O [CalibrationViewModel] criado pelo Hilt no celular. */
@HiltViewModel
class HiltCalibrationViewModel @Inject constructor(settings: SettingsRepository, player: ClickTrackPlayer) :
    CalibrationViewModel(settings, player)
