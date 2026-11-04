package com.rodrigomachado.riff.feature.report

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** O [ReportViewModel] criado pelo Hilt no celular. */
@HiltViewModel
class HiltReportViewModel @Inject constructor(
    saved: SavedStateHandle,
    sender: ReportSender,
    cooldown: ReportCooldown,
    deviceInfo: DeviceInfoSource,
) : ReportViewModel(saved, sender, cooldown, deviceInfo)
