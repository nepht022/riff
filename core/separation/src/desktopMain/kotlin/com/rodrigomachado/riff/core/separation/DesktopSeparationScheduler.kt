package com.rodrigomachado.riff.core.separation

import com.rodrigomachado.riff.core.data.SEPARATION_WORK_SUFFIX
import com.rodrigomachado.riff.core.data.SeparationQueue
import com.rodrigomachado.riff.core.data.SeparationScheduler
import com.rodrigomachado.riff.core.model.QueuedSeparation
import com.rodrigomachado.riff.core.model.SeparationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A fila de separações no PC (decisão 4 da etapa 4c): fica no banco e roda só com o Riff aberto, pela mesma
 * [SeparationQueueLoop] do celular, numa thread só dela em prioridade baixa (as threads internas do ONNX Runtime
 * ficam normais). Começa ao ser criado: apaga o que uma separação interrompida deixou (e as pastas sem linha no
 * banco) e continua a fila salva. [close] (ao fechar a
 * janela) cancela a da vez sem deixar pasta pela metade e a devolve à fila. Reaberto no mesmo processo com a
 * separação do fechado ainda terminando um trecho, espera ela acabar antes de mexer nas pastas.
 */
@Suppress("LongParameterList") // a fila, a separação e as pastas de trabalho
class DesktopSeparationScheduler(
    private val separationQueue: SeparationQueue,
    private val progress: SeparationProgress,
    separate: suspend (chartId: String, title: String) -> Boolean,
    isSeparated: suspend (chartId: String) -> Boolean,
    override val lowMemoryDevice: Boolean,
    /** Onde ficam as separações: as pastas `.tmp` que sobraram de uma separação interrompida saem ao abrir. */
    private val separationsDir: File,
    /** A pasta de rascunho da separação (a música decodificada): esvaziada ao abrir. */
    private val scratch: File,
    /**
     * Apaga as pastas de separação sem linha no banco (o Riff fechado entre guardar a pasta e anotá-la), ao abrir,
     * antes da fila.
     */
    private val pruneOrphans: suspend () -> Unit = {},
    private val closeWaitMs: Long = CLOSE_WAIT_MS,
    /** A da thread da separação: baixa, para não disputar com o jogo (nos testes sob carga, a normal). */
    threadPriority: Int = Thread.MIN_PRIORITY,
) : SeparationScheduler,
    AutoCloseable {
    private val control = SeparationQueueControl()
    private val handler = CoroutineExceptionHandler { _, e -> separationLog(TAG, "a fila de separações falhou", e) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)

    // O modelo bloqueia a thread a cada trecho: uma só para a separação, longe das da fila e da tela.
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "riff-separacao").apply {
            isDaemon = true
            priority = threadPriority
        }
    }.asCoroutineDispatcher()

    // A música cuja tentativa começou e ainda não terminou: fechar o Riff no meio dela não conta como tentativa.
    private val attempting = AtomicReference<String?>()
    private val closed = AtomicBoolean(false)
    private val wake = Channel<Unit>(Channel.CONFLATED)

    override val status: StateFlow<SeparationStatus> = progress.status
    override val waitingForCharger: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    override val queue: StateFlow<List<QueuedSeparation>> = combine(
        separationQueue.items,
        progress.status,
    ) { items, s ->
        val current = (s as? SeparationStatus.Running)?.chartId
        items.filterNot { it.chartId == current }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val loop = SeparationQueueLoop(
        queue = AttemptTracking(),
        separate = separate,
        isSeparated = isSeparated,
        progress = progress,
        control = control,
        onFinished = { _, _ -> }, // o fim (ou a falha) aparece na palheta do topo; no PC não há notificação
    )

    private val folder = separationsDir.absoluteFile.normalize()

    // A separação de um agendador fechado nesta pasta que ainda não acabou o trecho do modelo.
    private val previous: Job? = stillStopping[folder]

    private val running = scope.launch(worker) {
        // Antes de mexer nas pastas: ao terminar, a velha apaga a pasta temporária dela, a mesma da música na nova.
        previous?.join()
        deleteLeftovers()
        pruneLeftoverFolders()
        // Cada mudança da fila com música esperando acorda o laço; uma que chega enquanto ele termina fica guardada
        // (canal de uma vaga) e o acorda de novo.
        for (signal in wake) runQueue()
    }

    init {
        scope.launch { separationQueue.items.collect { if (it.isNotEmpty()) wake.trySend(Unit) } }
    }

    private suspend fun runQueue() {
        try {
            loop.run()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Fora da separação (o banco): fica no log; a próxima mudança da fila tenta de novo.
            separationLog(TAG, "a fila de separações parou", e)
        }
    }

    /** O Riff fechado no meio de uma separação (ou o processo morto): a pasta temporária e o rascunho ficaram. */
    private fun deleteLeftovers() {
        separationsDir.listFiles { file -> file.name.endsWith(SEPARATION_WORK_SUFFIX) }
            ?.forEach { it.deleteRecursively() }
        scratch.listFiles()?.forEach { it.deleteRecursively() }
    }

    private suspend fun pruneLeftoverFolders() {
        try {
            pruneOrphans()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // O banco falhou: a pasta sem linha fica até a próxima abertura; a fila segue.
            separationLog(TAG, "não deu para apagar as separações sem linha no banco", e)
        }
    }

    override fun enqueue(songs: List<QueuedSeparation>) {
        scope.launch { separationQueue.add(songs) }
    }

    override fun cancelCurrent(chartId: String) = control.cancelCurrent(chartId)

    override fun remove(chartId: String) {
        scope.launch { separationQueue.remove(chartId) }
    }

    override fun cancelAll() {
        scope.launch {
            // Nesta ordem: com a fila vazia, o laço não começa a próxima ao ver a atual cancelada.
            separationQueue.clear()
            control.cancelAny()
        }
        progress.set(SeparationStatus.Idle)
    }

    override fun dismissFailure() {
        if (progress.status.value is SeparationStatus.Failed) progress.set(SeparationStatus.Idle)
    }

    /**
     * Cancela a da vez e espera a limpeza (a pasta temporária apagada) por até [closeWaitMs]. O ONNX Runtime não
     * para no meio de um trecho: se o trecho passar disso, a janela fecha sem esperar — a separação cancelada não
     * guarda nada (a pasta definitiva só nasce no fim) e a pasta temporária sai na próxima abertura. A música fica
     * na fila, sem contar a tentativa. Um agendador aberto depois disso na mesma pasta (o mesmo processo; o app sai
     * do processo ao fechar) espera essa separação acabar antes de começar.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.cancel()
        val cleanedUp = runBlocking { withTimeoutOrNull(closeWaitMs) { running.join() } != null }
        if (!cleanedUp) {
            separationLog(TAG, "fechou com o modelo no meio de um trecho: a limpeza fica para a próxima")
            stillStopping[folder] = running
            running.invokeOnCompletion { stillStopping.remove(folder, running) }
        }
        attempting.getAndSet(null)?.let { chartId ->
            runBlocking { withTimeoutOrNull(UNDO_WAIT_MS) { separationQueue.undoAttempt(chartId) } }
        }
        worker.close()
    }

    /** A fila do laço, lembrando a música da tentativa em andamento. */
    private inner class AttemptTracking : SeparationQueue by separationQueue {
        override suspend fun startAttempt(chartId: String): Int {
            attempting.set(chartId)
            return separationQueue.startAttempt(chartId)
        }

        override suspend fun remove(chartId: String) {
            separationQueue.remove(chartId)
            attempting.compareAndSet(chartId, null)
        }
    }

    internal companion object {
        private const val TAG = "DesktopSeparation"

        // Fechar a janela leva no máximo isto a mais (mais o desconto da tentativa no banco).
        const val CLOSE_WAIT_MS = 1_500L
        private const val UNDO_WAIT_MS = 300L

        /** Por pasta de separações: a separação de um agendador fechado que o close() desistiu de esperar. */
        private val stillStopping = ConcurrentHashMap<File, Job>()
    }
}
