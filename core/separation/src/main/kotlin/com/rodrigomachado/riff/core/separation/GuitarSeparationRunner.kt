package com.rodrigomachado.riff.core.separation

import android.util.Log
import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.game.ChartAudioProbe
import com.rodrigomachado.riff.core.model.SeparationFailure
import com.rodrigomachado.riff.core.model.SeparationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import java.io.Closeable
import java.io.File

/** A música decodificada; fechar apaga o temporário. */
interface SongPcmHandle :
    SongPcm,
    Closeable

fun interface SongLoader {
    /** [keepGoing] falso interrompe a decodificação (cancelamento) com CancellationException. */
    fun load(uri: String, scratch: File, keepGoing: () -> Boolean): SongPcmHandle
}

/**
 * Uma separação do começo ao fim: confere o espaço, decodifica, separa trecho a trecho (com pausa pelo calor),
 * grava os `.m4a` numa pasta temporária e só então guarda. Qualquer falha deixa a música como estava.
 */
@Suppress("LongParameterList") // as dependências de cada etapa, trocadas por falsas nos testes
class GuitarSeparationRunner(
    private val repo: AiSeparationRepository,
    private val probe: ChartAudioProbe,
    private val loader: SongLoader,
    private val models: DemucsModelFactory,
    private val sinks: StemSinkFactory,
    private val progress: SeparationProgress,
    private val device: DeviceInfo,
    private val thermal: ThermalGate,
    private val scratch: File,
) {
    /** Verdadeiro se a separação terminou e foi guardada. */
    @Suppress("TooGenericExceptionCaught") // falha do modelo, do decodificador ou de memória: a música fica como estava
    suspend fun run(chartId: String, title: String): Boolean {
        if (device.freeBytes() < MIN_FREE_BYTES) return failed(title, SeparationFailure.NO_SPACE)
        deleteLeftoverDecodes()
        var work: File? = null
        return try {
            // Tudo dentro do try: nenhuma falha deixa o ícone preso em "separando".
            val uri = probe.audioOf(chartId)?.singleUri ?: return failed(title, SeparationFailure.NO_AUDIO)
            val dir = repo.workDir(chartId).also { work = it }
            val running = SeparationStatus.Running(chartId, title, progress = 0f, remainingMs = null)
            progress.set(running)
            separate(uri, dir, running)
            repo.commit(chartId, dir)
            progress.set(SeparationStatus.Idle)
            true
        } catch (e: CancellationException) {
            progress.set(SeparationStatus.Idle)
            throw e
        } catch (@Suppress("SwallowedException") e: EmptySongException) {
            failed(title, SeparationFailure.NO_AUDIO)
        } catch (e: Throwable) {
            Log.w(TAG, "não deu para separar $chartId", e)
            failed(title, SeparationFailure.ERROR)
        } finally {
            work?.deleteRecursively()
        }
    }

    private suspend fun separate(uri: String, work: File, running: SeparationStatus.Running) {
        val context = currentCoroutineContext()
        loader.load(uri, scratch) { context.isActive }.use { song ->
            if (song.frames == 0) throw EmptySongException()
            models.create(useArena = device.totalRamBytes >= ARENA_RAM_BYTES).use { model ->
                val sink = sinks.create(work)
                var finished = false
                try {
                    separateInto(song, model, sink, running)
                    sink.finish()
                    finished = true
                } finally {
                    if (!finished) sink.abort()
                }
            }
        }
    }

    private suspend fun separateInto(
        song: SongPcm,
        model: DemucsModel,
        sink: StemSink,
        running: SeparationStatus.Running,
    ) {
        val started = System.nanoTime()
        var current = running
        GuitarSeparator(model).separate(
            song,
            sink,
            onChunk = { done, total ->
                val elapsedMs = (System.nanoTime() - started) / NANOS_PER_MS
                current = current.copy(
                    progress = done.toFloat() / total,
                    remainingMs = elapsedMs / done * (total - done),
                )
                progress.set(current)
            },
            checkpoint = {
                currentCoroutineContext().ensureActive()
                thermal.awaitCool { paused -> progress.set(current.copy(pausedForHeat = paused)) }
            },
        )
    }

    /** O Android fechou o app no meio de uma separação: o decodificado daquela vez (~85 MB) ficou no cache. */
    private fun deleteLeftoverDecodes() {
        scratch.listFiles { file -> file.name.startsWith(DECODED_PREFIX) && file.name.endsWith(DECODED_SUFFIX) }
            ?.forEach { it.delete() }
    }

    private fun failed(title: String, reason: SeparationFailure): Boolean {
        progress.set(SeparationStatus.Failed(title, reason))
        return false
    }

    private companion object {
        const val TAG = "GuitarSeparation"

        // O decodificado de uma música de 4 min tem ~85 MB, mais a gravação: com menos, falta espaço no meio.
        const val MIN_FREE_BYTES = 200L shl 20
        const val ARENA_RAM_BYTES = 7L shl 30 // "8 GB" de fábrica aparece como ~7,4 GB de totalMem
        const val NANOS_PER_MS = 1_000_000L
    }
}

/** A música decodificou vazia (arquivo truncado ou sem trilha de áudio de verdade). */
private class EmptySongException : Exception("música vazia")
