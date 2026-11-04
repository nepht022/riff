package com.rodrigomachado.riff.core.separation

import ai.onnxruntime.OrtException
import com.rodrigomachado.riff.core.audio.FfmpegPcmSource
import com.rodrigomachado.riff.core.audio.PcmSourceOpener
import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.folder.FileRandomReadOpener
import com.rodrigomachado.riff.core.data.game.ChartAudioProbe
import java.io.File
import java.io.IOException
import java.lang.management.ManagementFactory

/** O PC (decisões 6 e 7 da etapa 4c): a memória física total e o espaço livre na pasta das separações. */
class DesktopDeviceInfo(private val dir: File, override val totalRamBytes: Long = physicalMemoryBytes()) : DeviceInfo {
    /** Com menos de 4 GB o Riff avisa antes de separar, como no celular. */
    val lowMemory: Boolean get() = totalRamBytes < LOW_RAM_BYTES

    override fun freeBytes(): Long = dir.apply { mkdirs() }.usableSpace

    private companion object {
        const val LOW_RAM_BYTES = 4L shl 30

        fun physicalMemoryBytes(): Long =
            (ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean)
                ?.totalMemorySize
                ?: Runtime.getRuntime().maxMemory()
    }
}

/** Abre o modelo: os bytes do `.onnx`, as threads e se usa a arena de memória. */
fun interface DemucsModelOpener {
    fun open(model: ByteArray, threads: Int, useArena: Boolean): DemucsModel
}

/** A separação no PC: o modelo dos recursos do app, o FFmpeg para ler a música e gravar as faixas. */
object DesktopSeparation {
    /** O `.onnx` do celular, copiado para os recursos do `:desktop` no build. */
    const val MODEL_RESOURCE = "/riff/${Demucs.MODEL_ASSET}"
    private const val TAG = "DesktopSeparation"

    // O atraso do codificador AAC do FFmpeg, medido uma vez por processo (na primeira separação).
    @Volatile private var encoderDelay: Int? = null

    /**
     * Os núcleos físicos menos um (a janela e o áudio seguem fluidos). O Java só dá os processadores lógicos: com
     * hyper-threading são 2 por núcleo, então a estimativa é a metade.
     */
    fun threads(logical: Int = Runtime.getRuntime().availableProcessors()): Int = (logical / 2 - 1).coerceAtLeast(1)

    /**
     * O modelo lido de [resource] a cada separação (fica na heap só até o ONNX Runtime copiá-lo). Sem o recurso, ou
     * com o ONNX Runtime que não carrega (DLL ausente, msvcp140), `create` falha com [IOException]: a separação
     * falha e o Riff continua aberto.
     */
    fun models(
        resource: String = MODEL_RESOURCE,
        threads: Int = threads(),
        opener: DemucsModelOpener = DemucsModelOpener { bytes, n, arena -> OrtDemucsModel(bytes, n, arena) },
    ): DemucsModelFactory = DemucsModelFactory { useArena ->
        val bytes = DesktopSeparation::class.java.getResourceAsStream(resource)?.use { it.readBytes() }
            ?: throw IOException("o modelo da IA ($resource) não está nos recursos do Riff")
        separationLog(
            TAG,
            "ONNX Runtime com $threads threads (${Runtime.getRuntime().availableProcessors()} processadores lógicos)" +
                ", arena ${if (useArena) "ligada" else "desligada"}",
        )
        try {
            opener.open(bytes, threads, useArena)
        } catch (e: LinkageError) {
            throw IOException("o ONNX Runtime não carregou neste PC", e)
        } catch (e: OrtException) {
            throw IOException("o ONNX Runtime não abriu o modelo da IA", e)
        }
    }

    /** O leitor de música do PC (48 kHz) devolvido na taxa do modelo, para medir o atraso do codificador. */
    private fun decoder(randomRead: RandomReadOpener) = PcmSourceOpener { uri ->
        ResamplingPcmSource(FfmpegPcmSource.open(uri, randomRead), Demucs.SAMPLE_RATE)
    }

    /** As faixas `.m4a`, sem o atraso do codificador (medido em [scratch] na primeira vez). */
    fun stemSinks(scratch: File, randomRead: RandomReadOpener = FileRandomReadOpener()): StemSinkFactory {
        val delay = encoderDelay ?: EncoderDelay.measure(scratch, decoder(randomRead)) { file, click, frames ->
            val aac = FfmpegAacFile(file)
            try {
                aac.write(click, frames)
                aac.finish()
            } finally {
                aac.release()
            }
        }.also { encoderDelay = it }
        return StemSinkFactory { dir -> SkippingStemSink(FfmpegStemSink(dir), delay) }
    }

    @Suppress("LongParameterList") // as dependências do GuitarSeparationRunner
    fun runner(
        repo: AiSeparationRepository,
        probe: ChartAudioProbe,
        randomRead: RandomReadOpener,
        progress: SeparationProgress,
        device: DeviceInfo,
        scratch: File,
        models: DemucsModelFactory = models(),
    ): GuitarSeparationRunner = GuitarSeparationRunner(
        repo = repo,
        probe = probe,
        loader = { uri, cache, keepGoing ->
            SongPcmLoader { FfmpegPcmSource.open(it, randomRead) }.load(uri, cache, keepGoing)
        },
        models = models,
        sinks = { dir -> stemSinks(scratch, randomRead).create(dir) },
        progress = progress,
        device = device,
        thermal = ThermalGate(hot = { false }, cool = { true }), // sem sensor de calor no PC (decisão 5)
        scratch = scratch,
    )
}
