package com.rodrigomachado.riff.core.separation

import com.rodrigomachado.riff.core.audio.FfmpegPcmSource
import com.rodrigomachado.riff.core.chart.sng.SngRef
import com.rodrigomachado.riff.core.chart.sng.SngWriter
import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.AiSeparationState
import com.rodrigomachado.riff.core.data.SEPARATED_BACKING
import com.rodrigomachado.riff.core.data.SEPARATED_GUITAR
import com.rodrigomachado.riff.core.data.SeparatedFiles
import com.rodrigomachado.riff.core.data.folder.FileFolderSource
import com.rodrigomachado.riff.core.data.folder.FileRandomReadOpener
import com.rodrigomachado.riff.core.data.folder.SngFolderSource
import com.rodrigomachado.riff.core.data.folder.pcUri
import com.rodrigomachado.riff.core.data.game.ChartAudioFinder
import com.rodrigomachado.riff.core.data.game.ChartAudioProbe
import com.rodrigomachado.riff.core.model.SeparationFailure
import com.rodrigomachado.riff.core.model.SeparationStatus
import com.rodrigomachado.riff.core.model.SeparationUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.minutes

class DesktopSeparationRunnerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val randomRead = FileRandomReadOpener()
    private val progress = SeparationProgress()
    private val separations by lazy { tmp.newFolder("separacoes") }
    private val repo by lazy { FolderRepository(separations) }
    private val probe = object : ChartAudioProbe {
        override suspend fun audioOf(chartId: String) =
            ChartAudioFinder.find(SngFolderSource(FileFolderSource(), randomRead).listChildren(chartId))
    }
    private val device = object : DeviceInfo {
        override val totalRamBytes = 16L shl 30
        override fun freeBytes() = 10L shl 30
    }

    /** Grava em `<raiz>/<id>.tmp` e guarda trocando o nome para `<raiz>/<id>`, como o repositório do app. */
    class FolderRepository(private val root: File) : AiSeparationRepository {
        val committed = mutableListOf<String>()
        override val separatedIds = MutableStateFlow(emptySet<String>())
        override val usage = MutableStateFlow(SeparationUsage(0, 0))
        override suspend fun stateOf(chartId: String): AiSeparationState? = null
        override suspend fun separatedFiles(chartId: String): SeparatedFiles? = null
        override fun workDir(chartId: String) = File(root, dirName(chartId) + ".tmp").apply { mkdirs() }
        override suspend fun commit(chartId: String, workDir: File) {
            check(workDir.renameTo(final(chartId)))
            committed += chartId
        }
        override suspend fun decline(chartId: String) = Unit
        override suspend fun resetDeclines() = Unit
        override suspend fun deleteAll() = Unit
        override suspend fun pruneOrphans() = Unit

        fun final(chartId: String) = File(root, dirName(chartId))
        private fun dirName(chartId: String) = "%08x".format(chartId.hashCode())
    }

    /** Metade da música para a guitarra, metade para o baixo: a soma das faixas volta a ser a música. */
    class HalfModel : DemucsModel {
        override fun run(mix: FloatArray, spec: FloatArray): DemucsOutput {
            val outMix = FloatArray(Demucs.SOURCES * mix.size)
            for (source in listOf(Demucs.GUITAR, BASS)) {
                for (i in mix.indices) outMix[source * mix.size + i] = mix[i] / 2
            }
            return DemucsOutput(FloatArray(Demucs.SOURCES * spec.size), outMix)
        }
        override fun close() = Unit

        private companion object {
            const val BASS = 1
        }
    }

    private fun runner(models: DemucsModelFactory = DemucsModelFactory { HalfModel() }) = DesktopSeparation.runner(
        repo = repo,
        probe = probe,
        randomRead = randomRead,
        progress = progress,
        device = device,
        scratch = tmp.newFolder("cache"),
        models = models,
    )

    /** Dez segundos de dois tons (média zero), em WAV 16 bits 44,1 kHz estéreo. */
    private fun songWav(): ByteArray {
        val frames = SONG_SECONDS * Demucs.SAMPLE_RATE
        val data = ByteBuffer.allocate(4 * frames).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val t = i.toDouble() / Demucs.SAMPLE_RATE
            data.putShort((AMPLITUDE * sin(2 * Math.PI * 220 * t) * Short.MAX_VALUE).toInt().toShort())
            data.putShort((AMPLITUDE * sin(2 * Math.PI * 330 * t) * Short.MAX_VALUE).toInt().toShort())
        }
        val header = ByteBuffer.allocate(WAV_HEADER).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(WAV_HEADER - 8 + data.capacity()).put("WAVE".toByteArray())
            .put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(2).putInt(Demucs.SAMPLE_RATE)
            .putInt(4 * Demucs.SAMPLE_RATE).putShort(4).putShort(16)
            .put("data".toByteArray()).putInt(data.capacity())
        return header.array() + data.array()
    }

    private fun folderChart(): String {
        val folder = tmp.newFolder("Banda - Musica").apply { File(this, "song.wav").writeBytes(songWav()) }
        return pcUri(folder)
    }

    private fun sngChart(): String {
        val sng = File(tmp.root, "Banda - Musica.sng").apply {
            writeBytes(SngWriter.write(mapOf("name" to "Musica"), mapOf("song.wav" to songWav())))
        }
        return SngRef.packageUri(pcUri(sng))
    }

    private fun decode(uri: String): FloatArray = FfmpegPcmSource.open(uri, randomRead).use { source ->
        val out = ArrayList<Float>()
        val block = FloatArray(BLOCK * source.channelCount)
        while (true) {
            val n = source.read(block, BLOCK)
            if (n == 0) break
            for (i in 0 until n * source.channelCount) out += block[i]
        }
        out.toFloatArray()
    }

    private fun assertSeparatedSumsToTheSong(chartId: String, songUri: String) {
        val dir = repo.final(chartId)
        val guitar = decode(File(dir, SEPARATED_GUITAR).path)
        val backing = decode(File(dir, SEPARATED_BACKING).path)
        val song = decode(songUri)
        // O último quadro AAC sai inteiro (até 1024 amostras a 44,1 kHz a mais no fim, em silêncio).
        val aacFrame = 2 * (1024 * OUT_RATE / Demucs.SAMPLE_RATE + 1)
        for ((name, stem) in listOf("guitarra" to guitar, "resto" to backing)) {
            assertTrue("$name: ${stem.size} × ${song.size}", stem.size - song.size in 0..aacFrame)
        }
        // Fora das bordas (o começo e o fim de cada faixa AAC), a soma é a música.
        val range = 2 * OUT_RATE until song.size - 2 * OUT_RATE
        var error = 0.0
        var energy = 0.0
        for (i in range) {
            val diff = guitar[i] + backing[i] - song[i]
            error += diff * diff
            energy += song[i] * song[i]
        }
        val relative = sqrt(error / energy)
        assertTrue("erro relativo da soma: $relative", relative < MAX_SUM_ERROR)
        assertFalse(File(separations, dir.name + ".tmp").exists())
    }

    @Test
    fun aSongInAFolderBecomesGuitarAndBacking() = runTest(timeout = 2.minutes) {
        val chart = folderChart()

        assertTrue(runner().run(chart, "Música"))

        assertEquals(listOf(chart), repo.committed)
        assertEquals(SeparationStatus.Idle, progress.status.value)
        assertSeparatedSumsToTheSong(chart, "$chart/song.wav")
    }

    @Test
    fun aSongInsideASngBecomesGuitarAndBacking() = runTest(timeout = 2.minutes) {
        val chart = sngChart()

        assertTrue(runner().run(chart, "Música"))

        assertEquals(listOf(chart), repo.committed)
        assertSeparatedSumsToTheSong(chart, SngRef.entryUri(SngRef.packageFileOf(chart)!!, "song.wav"))
    }

    @Test
    fun aModelThatFailsLeavesNoFolder() = runTest(timeout = 2.minutes) {
        val chart = folderChart()
        val failing = DemucsModelFactory {
            object : DemucsModel {
                override fun run(mix: FloatArray, spec: FloatArray) = throw IllegalStateException("o modelo falhou")
                override fun close() = Unit
            }
        }

        assertFalse(runner(failing).run(chart, "Música"))

        assertEquals(SeparationStatus.Failed("Música", SeparationFailure.ERROR), progress.status.value)
        assertTrue(repo.committed.isEmpty())
        assertTrue("sobrou ${separations.list()?.toList()}", separations.list().isNullOrEmpty())
    }

    @Test
    fun aMissingModelResourceFailsClearly() = runTest(timeout = 2.minutes) {
        val models = DesktopSeparation.models(resource = "/riff/nao-existe.onnx")

        val error = assertThrows(IOException::class.java) { models.create(useArena = true) }
        assertTrue(error.message, error.message!!.contains("/riff/nao-existe.onnx"))

        assertFalse(runner(models).run(folderChart(), "Música"))
        assertEquals(SeparationStatus.Failed("Música", SeparationFailure.ERROR), progress.status.value)
        assertTrue(separations.list().isNullOrEmpty())
    }

    /** O ONNX Runtime sem as DLLs (msvcp140) no Windows: a separação falha, o Riff continua aberto. */
    @Test
    fun anOnnxRuntimeThatDoesNotLoadIsAFailedSeparation() = runTest(timeout = 2.minutes) {
        val models = DesktopSeparation.models(resource = TEST_MODEL) { _, _, _ ->
            throw UnsatisfiedLinkError("onnxruntime.dll: Can't find dependent libraries")
        }

        val error = assertThrows(IOException::class.java) { models.create(useArena = true) }
        assertTrue(error.message, error.message!!.contains("ONNX Runtime"))

        assertFalse(runner(models).run(folderChart(), "Música"))
        assertEquals(SeparationStatus.Failed("Música", SeparationFailure.ERROR), progress.status.value)
        assertTrue(separations.list().isNullOrEmpty())
    }

    @Test
    fun theModelGetsTheBytesOfTheResourceAndTheThreads() {
        var got: Triple<ByteArray, Int, Boolean>? = null
        val models = DesktopSeparation.models(resource = TEST_MODEL, threads = 3) { bytes, threads, arena ->
            got = Triple(bytes, threads, arena)
            HalfModel()
        }

        models.create(useArena = false)

        val (bytes, threads, arena) = checkNotNull(got)
        assertEquals("modelo de teste", bytes.decodeToString().trim())
        assertEquals(3, threads)
        assertFalse(arena)
    }

    /** Os núcleos físicos (metade dos lógicos, pelo hyper-threading) menos um, para a janela e o áudio. */
    @Test
    fun threadsLeaveACoreForTheWindow() {
        assertEquals(7, DesktopSeparation.threads(logical = 16))
        assertEquals(3, DesktopSeparation.threads(logical = 8))
        assertEquals(1, DesktopSeparation.threads(logical = 4))
        assertEquals(1, DesktopSeparation.threads(logical = 2))
        assertEquals(1, DesktopSeparation.threads(logical = 1))
    }

    @Test
    fun lessThan4GbIsALowMemoryPc() {
        val dir = tmp.newFolder("separacoes-pc")

        assertTrue(DesktopDeviceInfo(dir, totalRamBytes = 3L shl 30).lowMemory)
        assertFalse(DesktopDeviceInfo(dir, totalRamBytes = 8L shl 30).lowMemory)
        assertTrue("memória física do PC", DesktopDeviceInfo(dir).totalRamBytes > 0)
        assertTrue(DesktopDeviceInfo(dir).freeBytes() > 0)
    }

    private companion object {
        const val SONG_SECONDS = 10
        const val AMPLITUDE = 0.3
        const val WAV_HEADER = 44
        const val BLOCK = 4096
        const val OUT_RATE = 48_000
        const val MAX_SUM_ERROR = 0.1
        const val TEST_MODEL = "/riff/modelo-de-teste.bin"
    }
}
