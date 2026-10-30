package com.rodrigomachado.riff.core.separation

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.AiSeparationState
import com.rodrigomachado.riff.core.data.SeparationQueue
import com.rodrigomachado.riff.core.model.SeparationStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

const val UNIQUE_WORK = "separacao-guitarra"

/** Separa a fila em primeiro plano ([SeparationQueueLoop]), com a notificação acompanhando o progresso. */
@HiltWorker
@Suppress("LongParameterList") // o que o laço da fila usa
class SeparationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runner: GuitarSeparationRunner,
    private val progress: SeparationProgress,
    private val queue: SeparationQueue,
    private val control: SeparationQueueControl,
    private val repo: AiSeparationRepository,
) : CoroutineWorker(context, params) {
    private val notifications = SeparationNotifications(context)

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val song = queue.next()
        return notifications.foregroundInfo(
            SeparationStatus.Running(song?.chartId.orEmpty(), song?.title.orEmpty(), 0f, null),
        )
    }

    override suspend fun doWork(): Result = coroutineScope {
        if (queue.next() == null) return@coroutineScope Result.success()
        // O Android 12+ não deixa virar primeiro plano com o app em segundo plano (ex.: retomada depois de
        // reiniciar o celular): a separação segue sem a notificação de progresso.
        var foreground = tryForeground(getForegroundInfo())
        val updates = launch {
            // A atual continua na fila até terminar: as que esperam são as outras.
            combine(
                progress.status,
                queue.items,
            ) { status, items -> status to items.size - 1 }.collect { (status, queued) ->
                if (foreground && status is SeparationStatus.Running) {
                    foreground = tryForeground(notifications.foregroundInfo(status, queued.coerceAtLeast(0)))
                }
            }
        }
        val loop = SeparationQueueLoop(
            queue = queue,
            separate = runner::run,
            isSeparated = { repo.stateOf(it) == AiSeparationState.SEPARATED },
            progress = progress,
            control = control,
        ) { song, ok ->
            val status = progress.status.value
            if (ok) {
                notifications.done(song.chartId, song.title)
            } else if (status is SeparationStatus.Failed) {
                notifications.failed(song.chartId, status)
            }
        }
        try {
            loop.run()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Fora da separação (o banco, cheio por exemplo): de novo mais tarde, com a espera do WorkManager, em vez
            // de um trabalho novo na hora pedido pelo scheduler.
            Log.w(TAG, "a fila de separações parou", e)
            Result.retry()
        } finally {
            updates.cancel()
        }
    }

    private suspend fun tryForeground(info: ForegroundInfo): Boolean = try {
        setForeground(info)
        true
    } catch (@Suppress("SwallowedException") e: IllegalStateException) {
        false
    }
}

private const val TAG = "SeparationWorker"
