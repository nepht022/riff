package com.rodrigomachado.riff.core.separation

import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.AiSeparationState
import com.rodrigomachado.riff.core.data.SeparatedFiles
import com.rodrigomachado.riff.core.data.game.ChartAudio
import com.rodrigomachado.riff.core.data.game.ChartAudioProbe
import com.rodrigomachado.riff.core.model.SeparationFailure
import com.rodrigomachado.riff.core.model.SeparationStatus
import com.rodrigomachado.riff.core.model.SeparationUsage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class GuitarSeparationRunnerTest : LoggingTest() {
    @get:Rule
    val tmp = TemporaryFolder()

    private val progress = SeparationProgress()
    private val repo = RunnerRepository()
    private val probe = object : ChartAudioProbe {
        var audio: ChartAudio? = ChartAudio.Single("content://m/song.opus")
        var failure: Exception? = null
        override suspend fun audioOf(chartId: String): ChartAudio? {
            failure?.let { throw it }
            return audio
        }
    }
    private var free = 1L shl 30
    private val device = object : DeviceInfo {
        override val totalRamBytes = 12L shl 30
        override fun freeBytes() = free
    }
    private var hot = false
    private val cooled = CompletableDeferred<Unit>()
    private val sinks = mutableListOf<RecordingSink>()

    inner class RunnerRepository : AiSeparationRepository {
        val committed = mutableListOf<String>()
        var commitFails = false
        override val separatedIds = MutableStateFlow(emptySet<String>())
        override val usage = MutableStateFlow(SeparationUsage(0, 0))
        override suspend fun stateOf(chartId: String): AiSeparationState? = null
        override suspend fun separatedFiles(chartId: String): SeparatedFiles? = null
        override fun workDir(chartId: String) = File(tmp.root, "$chartId.tmp").apply { mkdirs() }
        override suspend fun commit(chartId: String, workDir: File) {
            if (commitFails) throw IOException("o mapa saiu da biblioteca")
            committed += chartId
        }
        override suspend fun decline(chartId: String) = Unit
        override suspend fun resetDeclines() = Unit
        override suspend fun deleteAll() = Unit
        override suspend fun pruneOrphans() = Unit
    }

    class RecordingSink : ClosableStemSink {
        var frames = 0
        var finished = false
        var aborted = false
        override fun write(guitar: FloatArray, backing: FloatArray, frames: Int) {
            this.frames += frames
        }
        override fun finish() {
            finished = true
        }
        override fun abort() {
            aborted = true
        }
    }

    private fun runner(frames: Int = 400_000) = GuitarSeparationRunner(
        repo = repo,
        probe = probe,
        loader = { _, _, _ -> songHandle(frames) },
        models = { EchoModel() },
        sinks = { RecordingSink().also(sinks::add) },
        progress = progress,
        device = device,
        thermal = ThermalGate(hot = { hot }, cool = { cooled.isCompleted }, pollMs = 1000),
        scratch = tmp.root,
    )

    private fun songHandle(frames: Int) = object : SongPcmHandle {
        override val frames = frames
        override val mean = 0f
        override val std = 1f
        override fun sample(channel: Int, frame: Int) = 0.1f
        override fun close() = Unit
    }

    @Test
    fun aSeparationWritesCommitsAndGoesBackToIdle() = runTest {
        val seen = mutableListOf<SeparationStatus>()
        val watch = launch(UnconfinedTestDispatcher(testScheduler)) { progress.status.collect { seen += it } }

        assertTrue(runner().run("m", "Música"))
        watch.cancel()

        assertEquals(listOf("m"), repo.committed)
        assertTrue(sinks.single().finished)
        assertEquals(400_000, sinks.single().frames)
        assertTrue(seen.any { it is SeparationStatus.Running && it.progress == 0.5f })
        assertEquals(SeparationStatus.Idle, progress.status.value)
    }

    @Test
    fun withoutSpaceItDoesNotStart() = runTest {
        free = 10L shl 20

        assertFalse(runner().run("m", "Música"))

        assertEquals(SeparationStatus.Failed("Música", SeparationFailure.NO_SPACE), progress.status.value)
        assertTrue(sinks.isEmpty())
    }

    @Test
    fun aChartWithoutAudioFails() = runTest {
        probe.audio = null

        assertFalse(runner().run("m", "Música"))

        assertEquals(SeparationStatus.Failed("Música", SeparationFailure.NO_AUDIO), progress.status.value)
    }

    @Test
    fun aChartThatLeftTheLibraryFailsAndLeavesNoTempDir() = runTest {
        repo.commitFails = true

        assertFalse(runner().run("m", "Música"))

        assertEquals(SeparationStatus.Failed("Música", SeparationFailure.ERROR), progress.status.value)
        assertFalse(File(tmp.root, "m.tmp").exists())
    }

    @Test
    fun heatPausesAndCancellingWhilePausedCleansUp() = runTest {
        hot = true
        val job = launch { runner().run("m", "Música") }
        runCurrent()
        assertEquals(true, (progress.status.value as SeparationStatus.Running).pausedForHeat)

        job.cancel()
        runCurrent()

        assertEquals(SeparationStatus.Idle, progress.status.value)
        assertFalse(File(tmp.root, "m.tmp").exists())
        assertTrue(repo.committed.isEmpty())
        assertTrue(sinks.single().aborted) // os encoders não ficam abertos
    }

    @Test
    fun aFolderThatCannotBeReadFailsInsteadOfStayingRunning() = runTest {
        probe.failure = IOException("pasta sumiu")

        assertFalse(runner().run("m", "Música"))

        assertEquals(SeparationStatus.Failed("Música", SeparationFailure.ERROR), progress.status.value)
    }

    @Test
    fun aSuccessfulSeparationDoesNotAbortTheFiles() = runTest {
        runner().run("m", "Música")

        assertFalse(sinks.single().aborted)
    }

    /** O decodificado de uma música de 4 min tem ~85 MB: com menos de 200 MB livres, falta espaço no meio. */
    @Test
    fun lessThanTheDecodedSongFreeIsNoSpace() = runTest {
        free = 120L shl 20

        assertFalse(runner().run("m", "Música"))

        assertEquals(SeparationStatus.Failed("Música", SeparationFailure.NO_SPACE), progress.status.value)
    }

    /** O Android fechou o app no meio da decodificação: o temporário daquela vez é apagado na próxima. */
    @Test
    fun aDecodedSongLeftByAKilledProcessIsDeleted() = runTest {
        val leftover = File(tmp.root, "musica123.f32").apply { writeText("x") }
        val other = File(tmp.root, "outra.txt").apply { writeText("x") }

        runner().run("m", "Música")

        assertFalse(leftover.exists())
        assertTrue(other.exists())
    }

    @Test
    fun aSongThatDecodesEmptyHasNoAudio() = runTest {
        assertFalse(runner(frames = 0).run("m", "Música"))

        assertEquals(SeparationStatus.Failed("Música", SeparationFailure.NO_AUDIO), progress.status.value)
    }
}
