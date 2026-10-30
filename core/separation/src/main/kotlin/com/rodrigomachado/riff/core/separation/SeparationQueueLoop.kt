package com.rodrigomachado.riff.core.separation

import com.rodrigomachado.riff.core.data.SeparationQueue
import com.rodrigomachado.riff.core.model.QueuedSeparation
import com.rodrigomachado.riff.core.model.SeparationFailure
import com.rodrigomachado.riff.core.model.SeparationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/** Cancela só a separação em andamento (o trabalho roda no mesmo processo); a fila segue. */
@Singleton
class SeparationQueueControl @Inject constructor() {
    private val current = AtomicReference<Job?>()

    fun cancelCurrent() {
        current.get()?.cancel()
    }

    /** Roda [block] como a separação atual; nulo se ela foi cancelada sozinha (o trabalho continua). */
    internal suspend fun <T> runCurrent(block: suspend () -> T): T? = coroutineScope {
        val job = async { block() }
        current.set(job)
        try {
            job.await()
        } catch (@Suppress("SwallowedException") e: CancellationException) {
            currentCoroutineContext().ensureActive() // o trabalho inteiro foi cancelado: propaga
            null
        } finally {
            current.compareAndSet(job, null)
        }
    }
}

/**
 * Separa a fila até acabar, uma música por vez. A música só sai da fila depois de terminar (com sucesso, falha ou
 * cancelada sozinha): um trabalho interrompido (Android fechou o app, saiu da tomada) recomeça por ela.
 */
class SeparationQueueLoop(
    private val queue: SeparationQueue,
    private val separate: suspend (chartId: String, title: String) -> Boolean,
    private val progress: SeparationProgress,
    private val control: SeparationQueueControl,
    private val onFinished: (QueuedSeparation, ok: Boolean) -> Unit,
) {
    suspend fun run() {
        while (true) {
            val song = queue.next() ?: return
            val ok = control.runCurrent { separate(song.chartId, song.title) }
            queue.remove(song.chartId)
            if (ok == null) continue
            onFinished(song, ok)
            val status = progress.status.value
            // Sem espaço, as próximas falhariam do mesmo jeito.
            if (!ok && status is SeparationStatus.Failed && status.reason == SeparationFailure.NO_SPACE) queue.clear()
        }
    }
}
