package com.rodrigomachado.riff.core.audio

import android.os.Process
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Toca a guitarra e o resto da banda em faixas separadas, perfeitamente juntas: uma thread decodifica cada
 * faixa, mistura ([StemMixer]) e escreve numa saída só, cujos quadros tocados são o relógio. A guitarra é a
 * faixa 0 do mixer.
 */
@Suppress("TooManyFunctions") // ciclo de vida da thread de áudio, mistura e relógio ficam juntos de propósito
class StemSongPlayer(
    private val guitarUri: String,
    private val backingUris: List<String>,
    private val opener: PcmSourceOpener,
    private val sinks: AudioSinkFactory,
    private val log: (String, Throwable?) -> Unit = { message, error -> Log.w(TAG, message, error) },
    private val startThread: Boolean = true,
    private val nowMs: () -> Long = { SystemClock.uptimeMillis() },
    /** Roda na thread de áudio ao começar: prioridade de áudio, para o sistema não atrasá-la com o aparelho ocupado. */
    private val raiseAudioPriority: () -> Unit = { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) },
) : StemPlayer {
    private val lock = Object()

    // A thread de áudio escreve com isto travado; reposicionar troca faixas e saída sem ela no meio.
    private val renderLock = Object()

    // Quadro da música em que a saída atual começou (muda ao reposicionar: a saída nova conta do zero).
    private var baseFrames = 0L
    private var sources: List<PcmSource> = emptyList()
    private var sink: AudioSink? = null
    private var mixer: StemMixer? = null
    private var inputs: Array<FloatArray> = emptyArray()
    private var channels = IntArray(0)
    private var available = IntArray(0)
    private var ended = BooleanArray(0)
    private val out = FloatArray(BLOCK_FRAMES * 2)
    private val silence = FloatArray(BLOCK_FRAMES * 2)

    // Parte do último bloco que a saída ainda não aceitou (a escrita foi interrompida por uma pausa).
    private var pendingOffset = 0
    private var pendingFrames = 0

    // Cabeça de leitura parada antes do fim: o sistema às vezes segura o último pedaço do áudio.
    private var stalledHead = -1L
    private var stalledSinceMs = 0L
    private var sampleRate = 0
    private var writtenFrames = 0L
    private var positionAtRelease = 0L
    private var volume = 1f

    @Volatile private var playing = false

    @Volatile private var released = false

    @Volatile private var allEnded = false

    internal var audioThread: Thread? = null
        private set

    /** O [uri] é ignorado: as faixas vêm do construtor. */
    @Suppress("TooGenericExceptionCaught") // qualquer falha ao abrir vira IOException e o modo de um arquivo
    override suspend fun prepare(uri: String) = withContext(Dispatchers.IO) {
        val opened = mutableListOf<PcmSource>()
        try {
            (listOf(guitarUri) + backingUris).forEach { opened += opener.open(it) }
            val rates = opened.map { it.sampleRate }.distinct()
            if (rates.size != 1) throw IOException("faixas com taxas diferentes: $rates")
            if (opened.any { it.channelCount !in 1..2 }) throw IOException("só faixas mono ou estéreo")
            setUp(opened, rates.single())
        } catch (e: CancellationException) {
            opened.forEach { runCatching { it.close() } }
            throw e
        } catch (e: IOException) {
            fail(opened, e)
        } catch (e: RuntimeException) {
            fail(opened, IOException("faixa não pôde ser aberta", e))
        }
    }

    private fun fail(opened: List<PcmSource>, error: IOException): Nothing {
        opened.forEach { runCatching { it.close() } }
        log("Faixas separadas indisponíveis", error)
        throw error
    }

    private fun setUp(opened: List<PcmSource>, rate: Int) {
        sampleRate = rate
        inputs = Array(opened.size) { FloatArray(BLOCK_FRAMES * opened[it].channelCount) }
        channels = IntArray(opened.size) { opened[it].channelCount }
        available = IntArray(opened.size)
        ended = BooleanArray(opened.size)
        mixer = StemMixer(opened.size, StemMixer.rampFrames(rate)).also { it.masterGain = volume }
        val output = sinks.create(rate)
        synchronized(lock) {
            if (released) {
                opened.forEach { runCatching { it.close() } }
                output.release()
                return
            }
            sources = opened
            sink = output
        }
        if (startThread) audioThread = Thread(::loop, "riff-audio").apply { start() }
    }

    private fun loop() {
        raiseAudioPriority()
        while (!released) {
            synchronized(lock) {
                while (!released && !hasWork()) lock.wait()
            }
            if (!released) synchronized(renderLock) { renderOnce() }
        }
    }

    private fun hasWork(): Boolean = playing && (pendingFrames > 0 || !allEnded)

    /**
     * Grava o que sobrou do bloco anterior ou mistura um bloco novo e o grava. `false` quando não há mais nada:
     * todas as faixas acabaram e tudo foi entregue à saída.
     */
    internal fun renderOnce(): Boolean {
        val output = sink ?: return false
        if (released) return false
        if (pendingFrames == 0 && !allEnded) mixNextBlock(output)
        if (pendingFrames > 0) flushPending(output)
        return pendingFrames > 0 || !allEnded
    }

    private fun mixNextBlock(output: AudioSink) {
        val mix = mixer ?: return
        var frames = 0
        for (track in sources.indices) {
            available[track] = if (ended[track]) 0 else read(track)
            frames = maxOf(frames, available[track])
        }
        if (frames == 0) {
            allEnded = true
            pushOutTheEnd(output)
            return
        }
        mix.mix(inputs, channels, available, frames, out)
        pendingOffset = 0
        pendingFrames = frames
    }

    private fun flushPending(output: AudioSink) {
        val written = output.write(out, pendingOffset, pendingFrames)
        pendingOffset += written
        pendingFrames -= written
        writtenFrames += written
    }

    /** Silêncio depois da música, que não conta como música: empurra o último pedaço para o alto-falante. */
    private fun pushOutTheEnd(output: AudioSink) {
        repeat(END_SILENCE_BLOCKS) { output.write(silence, 0, BLOCK_FRAMES) }
    }

    @Suppress("TooGenericExceptionCaught") // decodificador quebrado no meio: a faixa vira silêncio
    private fun read(track: Int): Int {
        val count = try {
            sources[track].read(inputs[track], BLOCK_FRAMES)
        } catch (e: RuntimeException) {
            log("Faixa $track falhou no meio da música", e)
            0
        }
        if (count < BLOCK_FRAMES) ended[track] = true
        return count
    }

    override fun play() {
        val output = sink ?: return
        if (released) return
        output.start()
        synchronized(lock) {
            playing = true
            lock.notifyAll()
        }
    }

    override fun pause() {
        if (released) return
        playing = false
        val output = sink ?: return
        // A posição apresentada só se atualiza quando lida (a partida lê a cada 500 ms): lê agora, antes de
        // congelar, para a pista e o ponto de retomada não ficarem para trás.
        output.playedFrames
        output.pause()
    }

    /** Só com a música pausada: cada faixa volta ao ponto e uma saída nova começa dali (a antiga tinha o futuro). */
    override fun seekTo(positionMs: Long) {
        if (released || playing) return
        synchronized(renderLock) {
            val old = sink ?: return
            val frame = positionMs * sampleRate / 1_000
            for (track in sources.indices) {
                ended[track] = runCatching { sources[track].seekTo(frame) }
                    .onFailure { log("Faixa $track não pôde voltar", it) }
                    .isFailure
            }
            old.stop()
            old.release()
            sink = sinks.create(sampleRate)
            baseFrames = frame
            writtenFrames = 0
            pendingFrames = 0
            pendingOffset = 0
            allEnded = false
            stalledHead = -1
        }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        mixer?.masterGain = this.volume
    }

    override fun setGuitarAudible(audible: Boolean) {
        if (!released) mixer?.setTargetGain(GUITAR, if (audible) 1f else 0f)
    }

    override fun release() {
        synchronized(lock) {
            if (released) return
            positionAtRelease = positionMs
            released = true
            playing = false
            lock.notifyAll()
        }
        sink?.stop()
        audioThread?.join(JOIN_TIMEOUT_MS)
        sources.forEach { runCatching { it.close() } }
        sink?.release()
    }

    override val positionMs: Long
        get() {
            if (released) return positionAtRelease
            val output = sink ?: return 0
            return (baseFrames + output.playedFrames) * 1_000 / sampleRate
        }

    override val isPlaying: Boolean get() = playing && !released && !isCompleted

    override val isStarting: Boolean get() = isPlaying && sink?.isPresenting == false

    override val isCompleted: Boolean
        get() {
            val played = sink?.playedFrames ?: return false
            if (!allEnded || pendingFrames > 0) return false
            if (played >= writtenFrames) return true
            val now = nowMs()
            if (played != stalledHead) {
                stalledHead = played
                stalledSinceMs = now
            }
            return playing && now - stalledSinceMs >= STALL_MS
        }

    companion object {
        const val BLOCK_FRAMES = 1_024
        const val STALL_MS = 500L
        private const val END_SILENCE_BLOCKS = 4
        private const val GUITAR = 0
        private const val JOIN_TIMEOUT_MS = 1_000L
        private const val TAG = "RiffAudio"
    }
}
