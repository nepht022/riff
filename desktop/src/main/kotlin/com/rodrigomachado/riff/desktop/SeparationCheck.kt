package com.rodrigomachado.riff.desktop

import com.rodrigomachado.riff.core.audio.FfmpegPcmSource
import com.rodrigomachado.riff.core.chart.sng.SngRef
import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.AiSeparationState
import com.rodrigomachado.riff.core.data.SeparatedFiles
import com.rodrigomachado.riff.core.data.folder.FileFolderSource
import com.rodrigomachado.riff.core.data.folder.FileRandomReadOpener
import com.rodrigomachado.riff.core.data.folder.SngFolderSource
import com.rodrigomachado.riff.core.data.folder.pcUri
import com.rodrigomachado.riff.core.data.game.ChartAudio
import com.rodrigomachado.riff.core.data.game.ChartAudioFinder
import com.rodrigomachado.riff.core.data.game.ChartAudioProbe
import com.rodrigomachado.riff.core.model.SeparationStatus
import com.rodrigomachado.riff.core.model.SeparationUsage
import com.rodrigomachado.riff.core.separation.DesktopDeviceInfo
import com.rodrigomachado.riff.core.separation.DesktopSeparation
import com.rodrigomachado.riff.core.separation.SeparationProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.nio.file.Files
import kotlin.concurrent.thread

/**
 * Mede a separação por IA no PC: `./scripts/gw :desktop:runSeparationCheck --args="<pasta do mapa ou .sng>"`. Separa
 * a música com o modelo de verdade numa pasta temporária (fora da biblioteca) e imprime o tempo, a duração da
 * música, as threads, o pico da heap e o uso médio de CPU do processo.
 */
fun main(args: Array<String>) = runBlocking {
    // O Gradle separa o --args nos espaços: o caminho volta inteiro.
    val path = args.joinToString(" ").ifBlank { error("passe a pasta do mapa (ou o .sng)") }
    val target = File(path)
    val chartId = if (target.isFile) SngRef.packageUri(pcUri(target)) else pcUri(target)
    val randomRead = FileRandomReadOpener()
    val probe = object : ChartAudioProbe {
        override suspend fun audioOf(chartId: String) =
            ChartAudioFinder.find(SngFolderSource(FileFolderSource(), randomRead).listChildren(chartId))
    }
    val audio = probe.audioOf(chartId) as? ChartAudio.Single ?: error("$path não tem um áudio só para separar")
    val songMs = FfmpegPcmSource.open(audio.uri, randomRead).use { it.durationMs }
    val out = Files.createTempDirectory("riff-separacao-").toFile()
    val device = DesktopDeviceInfo(out)
    println("Música: $path (${songMs?.let { "%.1f s".format(it / MS_PER_S) } ?: "duração desconhecida"})")
    println(
        "Processadores lógicos: ${Runtime.getRuntime().availableProcessors()}, threads do ONNX Runtime: " +
            "${DesktopSeparation.threads()}, memória física: ${device.totalRamBytes shr MB_SHIFT} MB" +
            ", heap máxima: ${Runtime.getRuntime().maxMemory() shr MB_SHIFT} MB",
    )
    val sampler = Sampler().apply { start() }
    val started = System.nanoTime()
    val progress = SeparationProgress()
    val ok = DesktopSeparation.runner(
        repo = CheckRepository(out),
        probe = probe,
        randomRead = randomRead,
        progress = progress,
        device = device,
        scratch = out,
    ).run(chartId, target.nameWithoutExtension)
    val elapsedMs = (System.nanoTime() - started) / NS_PER_MS
    sampler.stop()
    println("Resultado: ${if (ok) "separada" else progress.status.value as? SeparationStatus.Failed}")
    println("Tempo total: %.1f s".format(elapsedMs / MS_PER_S))
    songMs?.let { println("Tempo / duração da música: %.2f×".format(elapsedMs.toDouble() / it)) }
    println("Pico da heap: ${sampler.peakHeap shr MB_SHIFT} MB (soma dos picos das áreas da heap)")
    println("CPU do processo (média): %.0f %%".format(sampler.meanCpu * PERCENT))
    println("Faixas em: ${out.absolutePath}")
}

/** Guarda em `<pasta>/separada` (a pasta temporária desta medida). */
private class CheckRepository(private val root: File) : AiSeparationRepository {
    override val separatedIds = MutableStateFlow(emptySet<String>())
    override val usage = MutableStateFlow(SeparationUsage(0, 0))
    override suspend fun stateOf(chartId: String): AiSeparationState? = null
    override suspend fun separatedFiles(chartId: String): SeparatedFiles? = null
    override fun workDir(chartId: String) = File(root, "separada.tmp").apply { mkdirs() }
    override suspend fun commit(chartId: String, workDir: File) {
        check(workDir.renameTo(File(root, "separada"))) { "não deu para guardar em $root" }
    }
    override suspend fun decline(chartId: String) = Unit
    override suspend fun resetDeclines() = Unit
    override suspend fun deleteAll() = Unit
    override suspend fun pruneOrphans() = Unit
}

/** Amostra o uso de CPU do processo a cada meio segundo; a heap pelos picos que a JVM registra. */
private class Sampler {
    @Volatile private var running = true
    private var cpuSum = 0.0
    private var samples = 0
    private lateinit var worker: Thread

    val meanCpu: Double get() = if (samples == 0) 0.0 else cpuSum / samples
    val peakHeap: Long
        get() = ManagementFactory.getMemoryPoolMXBeans()
            .filter { it.type == MemoryType.HEAP }
            .sumOf { it.peakUsage.used }

    fun start() {
        ManagementFactory.getMemoryPoolMXBeans().forEach { it.resetPeakUsage() }
        val os = ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean
        worker = thread(isDaemon = true, name = "medida") {
            while (running) {
                Thread.sleep(SAMPLE_MS)
                val load = os?.processCpuLoad ?: -1.0
                if (load >= 0) {
                    cpuSum += load
                    samples++
                }
            }
        }
    }

    fun stop() {
        running = false
        worker.join()
    }
}

private const val MS_PER_S = 1_000.0
private const val NS_PER_MS = 1_000_000L
private const val MB_SHIFT = 20
private const val PERCENT = 100
private const val SAMPLE_MS = 500L
