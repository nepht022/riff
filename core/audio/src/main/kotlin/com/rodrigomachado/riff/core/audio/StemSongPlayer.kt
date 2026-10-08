package com.rodrigomachado.riff.core.audio

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
class StemSongPlayer(
    private val guitarUri: String,
    private val backingUris: List<String>,
    private val opener: PcmSourceOpener,
    private val sinks: AudioSinkFactory,
    private val log: (String, Throwable?) -> Unit = { message, error -> Log.w(TAG, message, error) },
    private val startThread: Boolean = true,
) : StemPlayer {
    private val lock = Object()
    private var sources: List<PcmSource> = emptyList()
    private var sink: AudioSink? = null
    private var mixer: StemMixer? = null
    private var inputs: Array<FloatArray> = emptyArray()
    private var channels = IntArray(0)
    private var available = IntArray(0)
    private var ended = BooleanArray(0)
    private val out = FloatArray(BLOCK_FRAMES * 2)
    private var sampleRate = 0
    private var writtenFrames = 0L
    private var positionAtRelease = 0L

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
        mixer = StemMixer(opened.size, StemMixer.rampFrames(rate))
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
        while (!released) {
            synchronized(lock) {
                while (!released && (!playing || allEnded)) lock.wait()
            }
            if (!released) renderOnce()
        }
    }

    /** Lê um bloco de cada faixa, mistura e escreve. `false` quando todas as faixas acabaram. */
    internal fun renderOnce(): Boolean {
        if (released || allEnded) return false
        val output = sink ?: return false
        val mix = mixer ?: return false
        var frames = 0
        for (track in sources.indices) {
            available[track] = if (ended[track]) 0 else read(track)
            frames = maxOf(frames, available[track])
        }
        allEnded = frames == 0
        if (!allEnded) {
            mix.mix(inputs, channels, available, frames, out)
            output.write(out, frames)
            writtenFrames += frames
        }
        return !allEnded
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
        sink?.pause()
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
            return output.playedFrames * 1_000 / sampleRate
        }

    override val isPlaying: Boolean get() = playing && !released && !isCompleted

    override val isCompleted: Boolean
        get() = allEnded && (sink?.playedFrames ?: 0) >= writtenFrames

    companion object {
        const val BLOCK_FRAMES = 1_024
        private const val GUITAR = 0
        private const val JOIN_TIMEOUT_MS = 1_000L
        private const val TAG = "RiffAudio"
    }
}
