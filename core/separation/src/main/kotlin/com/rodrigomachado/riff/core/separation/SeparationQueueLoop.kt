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
    private class Current(val chartId: String, val job: Job)

    private val current = AtomicReference<Current?>()

    /** Cancela [chartId] se ainda for a atual (a tela pode estar mostrando a anterior, que acabou de terminar). */
    fun cancelCurrent(chartId: String) {
        current.get()?.takeIf { it.chartId == chartId }?.job?.cancel()
    }

    fun cancelAny() {
        current.get()?.job?.cancel()
    }

    /** Roda [block] como a separação de [chartId]; nulo se ela foi cancelada sozinha (o trabalho continua). */
    internal suspend fun <T> runCurrent(chartId: String, block: suspend () -> T): T? = coroutineScope {
        val job = async { block() }
        val entry = Current(chartId, job)
        current.set(entry)
        try {
            job.await()
        } catch (@Suppress("SwallowedException") e: CancellationException) {
            currentCoroutineContext().ensureActive() // o trabalho inteiro foi cancelado: propaga
            null
        } finally {
            current.compareAndSet(entry, null)
        }
    }
}

/**
 * Separa a fila até acabar, uma música por vez. A música só sai da fila depois de terminar (com sucesso, falha ou
 * cancelada sozinha): um trabalho interrompido (Android fechou o app, saiu da tomada) recomeça por ela, até
 * [MAX_ATTEMPTS] vezes.
 */
@Suppress("LongParameterList") // a fila, a separação e os avisos
class SeparationQueueLoop(
    private val queue: SeparationQueue,
    private val separate: suspend (chartId: String, title: String) -> Boolean,
    private val isSeparated: suspend (chartId: String) -> Boolean,
    private val progress: SeparationProgress,
    private val control: SeparationQueueControl,
    private val onFinished: (QueuedSeparation, ok: Boolean) -> Unit,
) {
    suspend fun run() {
        while (true) {
            val song = queue.next() ?: return
            val ok = separateOnce(song)
            queue.remove(song.chartId)
            if (ok != null) finished(song, ok)
        }
    }

    /** Nulo quando não há o que avisar: já estava separada ou foi cancelada sozinha. */
    private suspend fun separateOnce(song: QueuedSeparation): Boolean? = when {
        // O processo morreu entre guardar a separação e tirar da fila.
        isSeparated(song.chartId) -> null

        // Sempre interrompe o trabalho (falta de memória, limite de tempo): desiste para a fila andar.
        queue.startAttempt(song.chartId) > MAX_ATTEMPTS -> {
            progress.set(SeparationStatus.Failed(song.title, SeparationFailure.ERROR))
            false
        }

        else -> control.runCurrent(song.chartId) { separate(song.chartId, song.title) }
    }

    private suspend fun finished(song: QueuedSeparation, ok: Boolean) {
        onFinished(song, ok)
        val status = progress.status.value
        // Sem espaço, as próximas falhariam do mesmo jeito.
        if (!ok && status is SeparationStatus.Failed && status.reason == SeparationFailure.NO_SPACE) queue.clear()
    }
}

private const val MAX_ATTEMPTS = 3
