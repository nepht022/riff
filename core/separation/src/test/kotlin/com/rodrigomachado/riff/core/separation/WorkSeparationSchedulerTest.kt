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
import com.rodrigomachado.riff.core.model.SeparationFailure
import com.rodrigomachado.riff.core.model.SeparationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
class WorkSeparationSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val progress = SeparationProgress()
    private val device = object : DeviceInfo {
        override val totalRamBytes = 3L shl 30
        override fun freeBytes() = 1L shl 30
    }
    private lateinit var scheduler: WorkSeparationScheduler
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() = scope.cancel()

    private fun awaitStatus(expected: (SeparationStatus) -> Boolean, on: SeparationProgress = progress) =
        runBlocking { withTimeout(10_000) { on.status.first(expected) } }

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(WaitingWorkers).build(),
        )
        scheduler = WorkSeparationScheduler(context, progress, device, scope)
    }

    private fun work() = WorkManager.getInstance(context).getWorkInfosForUniqueWork(UNIQUE_WORK).get()

    @Test
    fun startingQueuesOneWorkAndShowsItRightAway() {
        assertTrue(scheduler.start("m", "Música"))

        assertEquals(1, work().size)
        assertEquals(SeparationStatus.Running("m", "Música", 0f, null), progress.status.value)
    }

    @Test
    fun aSecondSongWaitsItsTurnByBeingRefused() {
        scheduler.start("m", "Música")

        assertFalse(scheduler.start("n", "Outra"))
        assertEquals(1, work().size)
    }

    @Test
    fun cancellingStopsTheWork() {
        scheduler.start("m", "Música")

        scheduler.cancel()

        assertEquals(WorkInfo.State.CANCELLED, work().single().state)
        awaitStatus({ it == SeparationStatus.Idle })
    }

    @Test
    fun afterTheProcessDiesTheRunningWorkIsShownAgain() {
        scheduler.start("m", "Música")
        val fresh = SeparationProgress() // o processo morreu: o estado em memória recomeça em Idle

        WorkSeparationScheduler(context, fresh, device, scope)

        assertEquals(
            SeparationStatus.Running("m", "Música", 0f, null),
            awaitStatus({ it is SeparationStatus.Running }, on = fresh),
        )
    }

    @Test
    fun aFailureCanBeDismissedAndThePhoneIsLowOnMemory() {
        progress.set(SeparationStatus.Failed("Música", SeparationFailure.ERROR))

        scheduler.dismissFailure()

        assertEquals(SeparationStatus.Idle, progress.status.value)
        assertTrue(scheduler.lowMemoryDevice)
    }
}
