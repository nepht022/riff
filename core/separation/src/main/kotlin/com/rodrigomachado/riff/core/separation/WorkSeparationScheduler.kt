package com.rodrigomachado.riff.core.separation

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.rodrigomachado.riff.core.data.SeparationScheduler
import com.rodrigomachado.riff.core.model.SeparationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** Escopo que vive com o app: acompanha o WorkManager para o estado sobreviver à morte do processo. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SeparationScope

@Singleton
class WorkSeparationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val progress: SeparationProgress,
    device: DeviceInfo,
    @SeparationScope scope: CoroutineScope,
) : SeparationScheduler {
    override val status: StateFlow<SeparationStatus> = progress.status
    override val lowMemoryDevice = device.totalRamBytes < LOW_RAM_BYTES
    private val workManager get() = WorkManager.getInstance(context)

    init {
        scope.launch { workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK).collect(::reconcile) }
    }

    /**
     * O estado em memória some com o processo; o trabalho do WorkManager não. Um trabalho vivo volta a aparecer
     * (com a música das etiquetas) e um trabalho que acabou sem o runner avisar (cancelado antes de começar) some.
     */
    private fun reconcile(infos: List<WorkInfo>) {
        val live = infos.firstOrNull { !it.state.isFinished }
        val current = progress.status.value
        when {
            live != null && current !is SeparationStatus.Running -> progress.set(runningFrom(live) ?: return)

            live == null && infos.isNotEmpty() && current is SeparationStatus.Running -> progress.set(
                SeparationStatus.Idle,
            )
        }
    }

    private fun runningFrom(info: WorkInfo): SeparationStatus.Running? {
        val chartId = info.tags.firstNotNullOfOrNull {
            it.removePrefix(
                TAG_CHART,
            ).takeIf { _ -> it.startsWith(TAG_CHART) }
        }
        val title = info.tags.firstNotNullOfOrNull {
            it.removePrefix(
                TAG_TITLE,
            ).takeIf { _ -> it.startsWith(TAG_TITLE) }
        }
        return chartId?.let { SeparationStatus.Running(it, title.orEmpty(), progress = 0f, remainingMs = null) }
    }

    override fun start(chartId: String, title: String): Boolean {
        if (progress.status.value is SeparationStatus.Running) return false
        val request = OneTimeWorkRequestBuilder<SeparationWorker>()
            .setInputData(workDataOf(SeparationWorker.KEY_CHART_ID to chartId, SeparationWorker.KEY_TITLE to title))
            .addTag(TAG_CHART + chartId)
            .addTag(TAG_TITLE + title)
            .build()
        // O ícone aparece na hora, antes de o WorkManager começar.
        progress.set(SeparationStatus.Running(chartId, title, progress = 0f, remainingMs = null))
        workManager.enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
        return true
    }

    override fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK)
        // Cancelado antes de o runner começar, ninguém mais tiraria o "separando".
        progress.set(SeparationStatus.Idle)
    }

    override fun dismissFailure() {
        if (progress.status.value is SeparationStatus.Failed) progress.set(SeparationStatus.Idle)
    }

    private companion object {
        const val LOW_RAM_BYTES = 4L shl 30
        const val TAG_CHART = "separacao.chart="
        const val TAG_TITLE = "separacao.title="
    }
}
