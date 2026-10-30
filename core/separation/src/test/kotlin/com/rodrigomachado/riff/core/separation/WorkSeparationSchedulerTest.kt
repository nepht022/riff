package com.rodrigomachado.riff.core.separation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.model.QueuedSeparation
import com.rodrigomachado.riff.core.model.SeparationFailure
import com.rodrigomachado.riff.core.model.SeparationStatus
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** O WorkManager de teste roda o trabalho na hora: este Worker fica "separando" até ser cancelado. */
private object WaitingWorkers : WorkerFactory() {
    override fun createWorker(context: Context, workerClassName: String, params: WorkerParameters) =
        object : CoroutineWorker(context, params) {
            override suspend fun doWork(): Result = awaitCancellation()
        }
}

private class FakeSettings(initial: Settings = Settings.DEFAULT) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun update(transform: (Settings) -> Settings) = settings.update(transform)
}

@RunWith(RobolectricTestRunner::class)
class WorkSeparationSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val progress = SeparationProgress()
    private val queue = FakeSeparationQueue()
    private val settings = FakeSettings()
    private val control = SeparationQueueControl()
    private val device = object : DeviceInfo {
        override val totalRamBytes = 3L shl 30
        override fun freeBytes() = 1L shl 30
    }
    private val scheduler by lazy { newScheduler() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() = scope.cancel()

    private fun <T> await(flow: StateFlow<T>, expected: (T) -> Boolean): T =
        runBlocking { withTimeout(10_000) { flow.first(expected) } }

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(WaitingWorkers).build(),
        )
    }

    private fun newScheduler() = WorkSeparationScheduler(context, progress, device, queue, control, settings, scope)

    private fun work() = WorkManager.getInstance(context).getWorkInfosForUniqueWork(UNIQUE_WORK).get()

    private fun songs(vararg ids: String) = ids.map { QueuedSeparation(it, "Música $it") }

    private fun awaitWork(expected: (List<WorkInfo>) -> Boolean) =
        runBlocking { withTimeout(10_000) { while (!expected(work())) kotlinx.coroutines.delay(10) } }

    @Test
    fun enqueuingPutsTheSongsInTheQueueAndStartsTheWork() {
        scheduler.enqueue(songs("a", "b"))

        await(scheduler.queue) { it == songs("a", "b") }
        awaitWork { it.size == 1 }
        assertFalse(work().single().constraints.requiresCharging())
    }

    @Test
    fun moreSongsWhileOneIsSeparatingJoinTheSameWork() {
        scheduler.enqueue(songs("a"))
        awaitWork { it.size == 1 }

        scheduler.enqueue(songs("b"))

        await(scheduler.queue) { it == songs("a", "b") }
        assertEquals(1, work().size)
    }

    @Test
    fun theSongBeingSeparatedIsNotListedAsWaiting() {
        scheduler.enqueue(songs("a", "b"))

        progress.set(SeparationStatus.Running("a", "Música a", 0.1f, null))

        assertEquals(songs("b"), await(scheduler.queue) { it == songs("b") })
    }

    @Test
    fun onlyWhileChargingTheWorkWaitsForThePlug() {
        settings.settings.value = Settings.DEFAULT.copy(separateOnlyCharging = true)

        scheduler.enqueue(songs("a"))

        awaitWork { it.size == 1 }
        assertTrue(work().single().constraints.requiresCharging())
    }

    @Test
    fun turningTheOptionOffWhileWaitingStartsWithoutThePlug() {
        settings.settings.value = Settings.DEFAULT.copy(separateOnlyCharging = true)
        scheduler.enqueue(songs("a"))
        awaitWork { it.singleOrNull()?.state == WorkInfo.State.ENQUEUED }

        settings.settings.value = Settings.DEFAULT

        awaitWork { infos -> infos.any { !it.constraints.requiresCharging() && !it.state.isFinished } }
        assertEquals(1, work().count { !it.state.isFinished })
    }

    @Test
    fun waitingForThePlugIsShown() {
        settings.settings.value = Settings.DEFAULT.copy(separateOnlyCharging = true)

        scheduler.enqueue(songs("a"))

        await(scheduler.waitingForCharger) { it }
    }

    @Test
    fun removingASongTakesItOutOfTheQueue() {
        scheduler.enqueue(songs("a", "b"))
        await(scheduler.queue) { it.size == 2 }

        scheduler.remove("b")

        await(scheduler.queue) { it == songs("a") }
    }

    @Test
    fun cancellingAllEmptiesTheQueueAndStopsTheWork() {
        scheduler.enqueue(songs("a", "b"))
        awaitWork { it.size == 1 }
        progress.set(SeparationStatus.Running("a", "Música a", 0.1f, null))

        scheduler.cancelAll()

        await(scheduler.queue) { it.isEmpty() }
        assertTrue(queue.items.value.isEmpty())
        awaitWork { infos -> infos.all { it.state == WorkInfo.State.CANCELLED } }
        assertEquals(SeparationStatus.Idle, progress.status.value)
    }

    @Test
    fun aQueueLeftWithoutWorkIsStartedAgain() {
        // O processo morreu depois de gravar a fila e antes de pedir o trabalho: o app abre de novo.
        runBlocking { queue.add(songs("a")) }

        newScheduler()

        awaitWork { it.size == 1 }
    }

    @Test
    fun aFailureCanBeDismissedAndThePhoneIsLowOnMemory() {
        progress.set(SeparationStatus.Failed("Música", SeparationFailure.ERROR))

        scheduler.dismissFailure()

        assertEquals(SeparationStatus.Idle, progress.status.value)
        assertTrue(scheduler.lowMemoryDevice)
    }
}
