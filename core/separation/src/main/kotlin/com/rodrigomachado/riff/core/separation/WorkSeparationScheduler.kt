package com.rodrigomachado.riff.core.separation

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.rodrigomachado.riff.core.data.SeparationScheduler
import com.rodrigomachado.riff.core.model.SeparationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkSeparationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val progress: SeparationProgress,
    device: DeviceInfo,
) : SeparationScheduler {
    override val status: StateFlow<SeparationStatus> = progress.status
    override val lowMemoryDevice = device.totalRamBytes < LOW_RAM_BYTES

    override fun start(chartId: String, title: String): Boolean {
        if (progress.status.value is SeparationStatus.Running) return false
        val request = OneTimeWorkRequestBuilder<SeparationWorker>()
            .setInputData(workDataOf(SeparationWorker.KEY_CHART_ID to chartId, SeparationWorker.KEY_TITLE to title))
            .build()
        // O ícone aparece na hora, antes de o WorkManager começar.
        progress.set(SeparationStatus.Running(chartId, title, progress = 0f, remainingMs = null))
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
        return true
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }

    override fun dismissFailure() {
        if (progress.status.value is SeparationStatus.Failed) progress.set(SeparationStatus.Idle)
    }

    private companion object {
        const val LOW_RAM_BYTES = 4L shl 30
    }
}
