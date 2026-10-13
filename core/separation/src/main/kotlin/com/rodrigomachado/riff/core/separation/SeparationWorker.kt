package com.rodrigomachado.riff.core.separation

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.rodrigomachado.riff.core.model.SeparationStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

const val UNIQUE_WORK = "separacao-guitarra"

/** Roda o [GuitarSeparationRunner] em primeiro plano, com a notificação acompanhando o progresso. */
@HiltWorker
class SeparationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runner: GuitarSeparationRunner,
    private val progress: SeparationProgress,
) : CoroutineWorker(context, params) {
    private val notifications = SeparationNotifications(context)
    private val chartId get() = checkNotNull(inputData.getString(KEY_CHART_ID))
    private val title get() = inputData.getString(KEY_TITLE).orEmpty()

    override suspend fun getForegroundInfo(): ForegroundInfo =
        notifications.foregroundInfo(SeparationStatus.Running(chartId, title, 0f, null))

    override suspend fun doWork(): Result = coroutineScope {
        setForeground(getForegroundInfo())
        val updates = launch {
            progress.status.collect { status ->
                if (status is SeparationStatus.Running) setForeground(notifications.foregroundInfo(status))
            }
        }
        val ok = runner.run(chartId, title)
        updates.cancel()
        val status = progress.status.value
        if (ok) {
            notifications.done(title)
        } else if (status is SeparationStatus.Failed) {
            notifications.failed(status)
        }
        if (ok) Result.success() else Result.failure()
    }

    companion object {
        const val KEY_CHART_ID = "chartId"
        const val KEY_TITLE = "title"
    }
}
