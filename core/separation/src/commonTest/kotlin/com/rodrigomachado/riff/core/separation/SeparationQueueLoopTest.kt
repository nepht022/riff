package com.rodrigomachado.riff.core.separation

import com.rodrigomachado.riff.core.data.SeparationQueue
import com.rodrigomachado.riff.core.model.QueuedSeparation
import com.rodrigomachado.riff.core.model.SeparationFailure
import com.rodrigomachado.riff.core.model.SeparationStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A fila em memória, na ordem de chegada. */
class FakeSeparationQueue(vararg ids: String) : SeparationQueue {
    override val items = MutableStateFlow(ids.map { QueuedSeparation(it, "Música $it") })

    override suspend fun add(songs: List<QueuedSeparation>) {
        items.value += songs.filter { song -> items.value.none { it.chartId == song.chartId } }
    }

    override suspend fun next() = items.value.firstOrNull()

    override suspend fun remove(chartId: String) {
        items.value = items.value.filterNot { it.chartId == chartId }
    }

    override suspend fun clear() {
        items.value = emptyList()
    }

    val attempts = mutableMapOf<String, Int>()

    override suspend fun startAttempt(chartId: String): Int {
        attempts[chartId] = (attempts[chartId] ?: 0) + 1
        return attempts.getValue(chartId)
    }

    override suspend fun undoAttempt(chartId: String) {
        attempts[chartId] = ((attempts[chartId] ?: 0) - 1).coerceAtLeast(0)
    }
}

class SeparationQueueLoopTest {
    private val progress = SeparationProgress()
    private val control = SeparationQueueControl()
    private val separated = mutableListOf<String>()
    private val finished = mutableListOf<Pair<String, Boolean>>()

    private val alreadySeparated = mutableSetOf<String>()

    private fun loop(queue: SeparationQueue, run: suspend (String) -> Boolean) = SeparationQueueLoop(
        queue = queue,
        separate = { chartId, _ ->
            separated += chartId
            run(chartId)
        },
        isSeparated = { it in alreadySeparated },
        progress = progress,
        control = control,
        onFinished = { song, ok -> finished += song.chartId to ok },
    )

    @Test
    fun separatesTheWholeQueueInOrder() = runTest {
        val queue = FakeSeparationQueue("a", "b", "c")

        loop(queue) { true }.run()

        assertEquals(listOf("a", "b", "c"), separated)
        assertEquals(listOf("a" to true, "b" to true, "c" to true), finished)
        assertTrue(queue.items.value.isEmpty())
    }

    @Test
    fun aFailureDoesNotStopTheQueue() = runTest {
        val queue = FakeSeparationQueue("a", "b")

        loop(queue) { id ->
            if (id == "a") progress.set(SeparationStatus.Failed("Música a", SeparationFailure.ERROR))
            id != "a"
        }.run()

        assertEquals(listOf("a" to false, "b" to true), finished)
        assertTrue(queue.items.value.isEmpty())
    }

    @Test
    fun withoutSpaceTheRestWouldFailTooSoTheQueueEmpties() = runTest {
        val queue = FakeSeparationQueue("a", "b", "c")

        loop(queue) {
            progress.set(SeparationStatus.Failed("Música a", SeparationFailure.NO_SPACE))
            false
        }.run()

        assertEquals(listOf("a"), separated)
        assertTrue(queue.items.value.isEmpty())
    }

    @Test
    fun cancellingTheCurrentSongMovesOnToTheNext() = runTest {
        val queue = FakeSeparationQueue("a", "b")
        val started = CompletableDeferred<Unit>()
        val job = launch {
            loop(queue) { id ->
                if (id == "a") {
                    started.complete(Unit)
                    awaitCancellation()
                }
                true
            }.run()
        }
        started.await()

        control.cancelCurrent("b") // já não é a atual: nada acontece
        control.cancelCurrent("a")
        job.join()

        assertEquals(listOf("a", "b"), separated)
        assertEquals(listOf("b" to true), finished)
        assertTrue(queue.items.value.isEmpty())
    }

    @Test
    fun anInterruptedWorkKeepsTheSongForNextTime() = runTest {
        val queue = FakeSeparationQueue("a", "b")
        val job = launch { loop(queue) { awaitCancellation() }.run() }
        runCurrent()

        job.cancel()
        job.join()

        assertEquals(listOf("a", "b"), queue.items.value.map { it.chartId })
        assertTrue(finished.isEmpty())
    }

    @Test
    fun aSongAddedWhileSeparatingIsAlsoSeparated() = runTest {
        val queue = FakeSeparationQueue("a")

        loop(queue) { id ->
            if (id == "a") queue.add(listOf(QueuedSeparation("b", "Música b")))
            true
        }.run()

        assertEquals(listOf("a", "b"), separated)
    }

    @Test
    fun aSongThatKeepsInterruptingTheWorkIsGivenUpAfterThreeTries() = runTest {
        val queue = FakeSeparationQueue("a", "b")
        queue.attempts["a"] = 3 // três trabalhos morreram no meio dela

        loop(queue) { true }.run()

        assertEquals(listOf("b"), separated)
        assertEquals(listOf("a" to false, "b" to true), finished)
        assertTrue(queue.items.value.isEmpty())
    }

    @Test
    fun aSongAlreadySeparatedIsNotSeparatedAgain() = runTest {
        val queue = FakeSeparationQueue("a", "b")
        alreadySeparated += "a" // o processo morreu entre guardar e tirar da fila

        loop(queue) { true }.run()

        assertEquals(listOf("b"), separated)
        assertTrue(queue.items.value.isEmpty())
    }
}
