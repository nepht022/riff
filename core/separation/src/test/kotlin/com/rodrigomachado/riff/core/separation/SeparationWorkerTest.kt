package com.rodrigomachado.riff.core.separation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.util.concurrent.ListenableFuture
import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.AiSeparationState
import com.rodrigomachado.riff.core.data.SeparatedFiles
import com.rodrigomachado.riff.core.data.game.ChartAudio
import com.rodrigomachado.riff.core.data.game.ChartAudioProbe
import com.rodrigomachado.riff.core.model.SeparationUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SeparationWorkerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val progress = SeparationProgress()

    private val repo = object : AiSeparationRepository {
        val committed = mutableListOf<String>()
        override val separatedIds = MutableStateFlow(emptySet<String>())
        override val usage = MutableStateFlow(SeparationUsage(0, 0))
        override suspend fun stateOf(chartId: String): AiSeparationState? = null
        override suspend fun separatedFiles(chartId: String): SeparatedFiles? = null
        override fun workDir(chartId: String) = File(tmp.root, "$chartId.tmp").apply { mkdirs() }
        override suspend fun commit(chartId: String, workDir: File) {
            committed += chartId
        }
        override suspend fun decline(chartId: String) = Unit
        override suspend fun resetDeclines() = Unit
        override suspend fun deleteAll() = Unit
        override suspend fun pruneOrphans() = Unit
    }

    private val runner by lazy {
        GuitarSeparationRunner(
            repo = repo,
            probe = object : ChartAudioProbe {
                override suspend fun audioOf(chartId: String) = ChartAudio.Single("content://m/song.opus")
            },
            loader = { _, _, _ ->
                object : SongPcmHandle {
                    override val frames = 100_000
                    override val mean = 0f
                    override val std = 1f
                    override fun sample(channel: Int, frame: Int) = 0.1f
                    override fun close() = Unit
                }
            },
            models = { EchoModel() },
            sinks = {
                object : ClosableStemSink {
                    override fun write(guitar: FloatArray, backing: FloatArray, frames: Int) = Unit
                    override fun finish() = Unit
                    override fun abort() = Unit
                }
            },
            progress = progress,
            device = object : DeviceInfo {
                override val totalRamBytes = 12L shl 30
                override fun freeBytes() = 1L shl 30
            },
            thermal = ThermalGate(hot = { false }, cool = { true }),
            scratch = tmp.root,
        )
    }

    /** O Android 12+ não deixa virar primeiro plano com o app em segundo plano (ex.: retomada depois de reiniciar). */
    private object RefusingForeground : ForegroundUpdater {
        override fun setForegroundAsync(
            context: Context,
            id: UUID,
            foregroundInfo: androidx.work.ForegroundInfo,
        ): ListenableFuture<Void> = throw IllegalStateException("ForegroundServiceStartNotAllowedException")
    }

    private val queue = FakeSeparationQueue("m", "n")

    @Test
    fun withoutForegroundTheWholeQueueStillRuns() = runTest {
        val worker = TestListenableWorkerBuilder<SeparationWorker>(context)
            .setForegroundUpdater(RefusingForeground)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        context: Context,
                        name: String,
                        params: WorkerParameters,
                    ): ListenableWorker = SeparationWorker(
                        context,
                        params,
                        runner,
                        progress,
                        queue,
                        SeparationQueueControl(),
                    )
                },
            )
            .build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(listOf("m", "n"), repo.committed)
        assertEquals(emptyList<Any>(), queue.items.value)
    }
}
