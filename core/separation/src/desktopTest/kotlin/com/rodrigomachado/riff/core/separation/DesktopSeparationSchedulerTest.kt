package com.rodrigomachado.riff.core.separation

import com.rodrigomachado.riff.core.model.QueuedSeparation
import com.rodrigomachado.riff.core.model.SeparationStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * O agendador do PC: a fila salva roda com o Riff aberto, numa thread só dela; fechar cancela a da vez sem deixar
 * pasta pela metade e devolve a música à fila, rápido mesmo com o modelo no meio de um trecho.
 */
class DesktopSeparationSchedulerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val progress = SeparationProgress()
    private val separated = Collections.synchronizedList(mutableListOf<String>())
    private val started = CountDownLatch(1)
    private val separations by lazy { tmp.newFolder("separacoes") }
    private val scratch by lazy { tmp.newFolder("temp") }
    private var opened: DesktopSeparationScheduler? = null

    @After
    fun close() {
        opened?.close()
    }

    private fun scheduler(
        queue: FakeSeparationQueue,
        separate: suspend (chartId: String) -> Boolean = { chartId ->
            separated += chartId
            true
        },
    ) = DesktopSeparationScheduler(
        separationQueue = queue,
        progress = progress,
        separate = { chartId, _ -> separate(chartId) },
        isSeparated = { false },
        lowMemoryDevice = true,
        separationsDir = separations,
        scratch = scratch,
    ).also { opened = it }

    /** Como o runner: grava numa pasta `.tmp` e a apaga se não terminar. */
    private suspend fun separateInWorkDir(chartId: String, block: suspend () -> Unit): Boolean {
        val work = File(separations, "$chartId.tmp").apply { mkdirs() }
        File(work, "guitar.m4a").writeText("metade")
        progress.set(SeparationStatus.Running(chartId, "Música $chartId", progress = 0f, remainingMs = null))
        try {
            started.countDown()
            block()
            return true
        } finally {
            work.deleteRecursively()
        }
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "não aconteceu: $what" }
            Thread.sleep(POLL_MS)
        }
    }

    @Test
    fun theSavedQueueContinuesWhenTheAppOpens() {
        scheduler(FakeSeparationQueue("a", "b"))

        waitUntil("separar a fila salva") { separated.toList() == listOf("a", "b") }
    }

    @Test
    fun enqueuedSongsAreSeparatedInOrder() {
        val queue = FakeSeparationQueue()
        val scheduler = scheduler(queue)

        scheduler.enqueue(listOf(QueuedSeparation("a", "A"), QueuedSeparation("b", "B")))

        waitUntil("separar as duas") { separated.toList() == listOf("a", "b") }
        waitUntil("esvaziar a fila") { queue.items.value.isEmpty() }
    }

    @Test
    fun onThePcTheQueueNeverWaitsForTheChargerAndLowMemoryComesFromThePc() {
        val scheduler = scheduler(FakeSeparationQueue())

        assertFalse(scheduler.waitingForCharger.value)
        assertTrue(scheduler.lowMemoryDevice)
        assertTrue(scheduler.isAvailable)
    }

    @Test
    fun leftoversOfAnInterruptedSeparationAreDeletedOnOpening() {
        val orphan = File(separations, "interrompida.tmp").apply { mkdirs() }
        File(orphan, "guitar.m4a").writeText("metade")
        val done = File(separations, "pronta").apply { mkdirs() }
        File(done, "guitar.m4a").writeText("inteira")
        val decoded = File(scratch, "riff-decodificada.f32").apply { writeText("pcm") }

        scheduler(FakeSeparationQueue())

        waitUntil("apagar a pasta temporária") { !orphan.exists() && !decoded.exists() }
        assertTrue("a separação pronta ficou", File(done, "guitar.m4a").isFile)
    }

    @Test
    fun closingWaitsForTheCleanupAndPutsTheSongBackInTheQueue() {
        val queue = FakeSeparationQueue("a", "b")
        val scheduler = scheduler(queue) { chartId ->
            separateInWorkDir(chartId) {
                // Um modelo que confere o cancelamento entre trechos curtos.
                while (true) {
                    Thread.sleep(SHORT_CHUNK_MS)
                    currentCoroutineContext().ensureActive()
                }
            }
        }
        assertTrue(started.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))

        scheduler.close()

        assertFalse("sobrou a pasta temporária", File(separations, "a.tmp").exists())
        assertEquals(listOf("a", "b"), queue.items.value.map { it.chartId })
        assertEquals("fechar não conta como tentativa", 0, queue.attemptsOf("a"))
    }

    /** O ONNX Runtime não para no meio de um trecho: a janela fecha sem esperar o trecho acabar. */
    @Test
    fun closingIsQuickEvenWithTheModelInTheMiddleOfAChunk() {
        val queue = FakeSeparationQueue("a")
        val scheduler = scheduler(queue) { chartId ->
            separateInWorkDir(chartId) {
                uninterruptibleSleep(LONG_CHUNK_MS)
                currentCoroutineContext().ensureActive()
            }
        }
        assertTrue(started.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))

        val begin = System.nanoTime()
        scheduler.close()
        val elapsedMs = (System.nanoTime() - begin) / NANOS_PER_MS

        println("close() com o modelo no meio de um trecho: $elapsedMs ms")
        assertTrue("close() levou $elapsedMs ms", elapsedMs < MAX_CLOSE_MS)
        assertFalse("nenhuma pasta definitiva", File(separations, "a").exists())
        assertEquals(listOf("a"), queue.items.value.map { it.chartId })
        assertEquals(0, queue.attemptsOf("a"))
    }

    @Test
    fun cancelAllEmptiesTheQueueAndStopsTheCurrentOne() {
        val queue = FakeSeparationQueue("a", "b")
        val scheduler = scheduler(queue) { chartId ->
            separateInWorkDir(chartId) {
                while (true) {
                    Thread.sleep(SHORT_CHUNK_MS)
                    currentCoroutineContext().ensureActive()
                }
            }
        }
        assertTrue(started.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))

        scheduler.cancelAll()

        waitUntil("esvaziar a fila") { queue.items.value.isEmpty() }
        waitUntil("apagar a pasta temporária") { !File(separations, "a.tmp").exists() }
        assertEquals(SeparationStatus.Idle, progress.status.value)
    }

    @Test
    fun cancellingTheCurrentOneMovesToTheNext() {
        val queue = FakeSeparationQueue("a", "b")
        val scheduler = scheduler(queue) { chartId ->
            separated += chartId
            if (chartId == "a") {
                separateInWorkDir(chartId) {
                    while (true) {
                        Thread.sleep(SHORT_CHUNK_MS)
                        currentCoroutineContext().ensureActive()
                    }
                }
            } else {
                true
            }
        }
        assertTrue(started.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        waitUntil("a fila mostrar só a que espera") { scheduler.queue.value.map { it.chartId } == listOf("b") }

        scheduler.cancelCurrent("a")

        waitUntil("separar a seguinte") { separated.toList() == listOf("a", "b") }
        waitUntil("esvaziar a fila") { queue.items.value.isEmpty() }
    }

    private fun FakeSeparationQueue.attemptsOf(chartId: String) = runBlocking { attempts[chartId] ?: 0 }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 10L
        const val SHORT_CHUNK_MS = 20L
        const val LONG_CHUNK_MS = 8_000L
        const val MAX_CLOSE_MS = 2_000L
        const val NANOS_PER_MS = 1_000_000L

        /** Como uma chamada nativa: nem a interrupção da thread a para. */
        fun uninterruptibleSleep(ms: Long) {
            val end = System.nanoTime() + ms * NANOS_PER_MS
            while (System.nanoTime() < end) {
                try {
                    Thread.sleep(POLL_MS)
                } catch (@Suppress("SwallowedException") e: InterruptedException) {
                    // ignora, como o ONNX Runtime
                }
            }
        }
    }
}
