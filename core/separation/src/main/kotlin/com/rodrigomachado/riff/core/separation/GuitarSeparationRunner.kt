package com.rodrigomachado.riff.core.separation

import android.util.Log
import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.game.ChartAudioProbe
import com.rodrigomachado.riff.core.model.SeparationFailure
import com.rodrigomachado.riff.core.model.SeparationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.Closeable
import java.io.File

/** A música decodificada; fechar apaga o temporário. */
interface SongPcmHandle :
    SongPcm,
    Closeable

fun interface SongLoader {
    fun load(uri: String, scratch: File): SongPcmHandle
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
        val uri = probe.audioOf(chartId)?.singleUri ?: return failed(title, SeparationFailure.NO_AUDIO)
        val work = repo.workDir(chartId)
        val running = SeparationStatus.Running(chartId, title, progress = 0f, remainingMs = null)
        progress.set(running)
        return try {
            separate(uri, work, running)
            repo.commit(chartId, work)
            progress.set(SeparationStatus.Idle)
            true
        } catch (e: CancellationException) {
            progress.set(SeparationStatus.Idle)
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "não deu para separar $chartId", e)
            failed(title, SeparationFailure.ERROR)
        } finally {
            work.deleteRecursively()
        }
    }

    private suspend fun separate(uri: String, work: File, running: SeparationStatus.Running) {
        loader.load(uri, scratch).use { song ->
            models.create(useArena = device.totalRamBytes >= ARENA_RAM_BYTES).use { model ->
                val sink = sinks.create(work)
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
                sink.finish()
            }
        }
    }

    private fun failed(title: String, reason: SeparationFailure): Boolean {
        progress.set(SeparationStatus.Failed(title, reason))
        return false
    }

    private companion object {
        const val TAG = "GuitarSeparation"
        const val MIN_FREE_BYTES = 50L shl 20
        const val ARENA_RAM_BYTES = 7L shl 30 // "8 GB" de fábrica aparece como ~7,4 GB de totalMem
        const val NANOS_PER_MS = 1_000_000L
    }
}
