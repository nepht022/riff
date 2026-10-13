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
import kotlinx.coroutines.awaitCancellation
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

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(WaitingWorkers).build(),
        )
        scheduler = WorkSeparationScheduler(context, progress, device)
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
    }

    @Test
    fun aFailureCanBeDismissedAndThePhoneIsLowOnMemory() {
        progress.set(SeparationStatus.Failed("Música", SeparationFailure.ERROR))

        scheduler.dismissFailure()

        assertEquals(SeparationStatus.Idle, progress.status.value)
        assertTrue(scheduler.lowMemoryDevice)
    }
}
