package com.rodrigomachado.riff.core.separation

import com.rodrigomachado.riff.core.audio.PcmSource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Converte a taxa de [source] para [targetRate] com sinc janelado (Blackman, [TAPS] de cada lado, corte em 90 % do
 * Nyquist menor). O Opus dos mapas decodifica a 48 kHz e o modelo pede 44,1 kHz. Na mesma taxa, só repassa.
 */
class ResamplingPcmSource(private val source: PcmSource, targetRate: Int) : PcmSource {
    override val sampleRate = targetRate
    override val channelCount = source.channelCount
    private val step = source.sampleRate.toDouble() / targetRate
    private val cutoff = 0.9 * min(1.0, 1.0 / step)
    private val history = Array(channelCount) { FloatArray(0) }
    private var historyStart = 0L // quadro de entrada de history[c][0]
    private var available = 0
    private var ended = false
    private var nextOut = 0L
    private val block = FloatArray(BLOCK * channelCount)

    // O kernel tabelado (com interpolação linear): seno e cossenos por amostra deixavam a decodificação lenta.
    private val table = FloatArray(TAPS * RESOLUTION + 2) { kernel(it.toDouble() / RESOLUTION).toFloat() }
    private val weights = FloatArray(2 * TAPS)

    override fun read(buffer: FloatArray, maxFrames: Int): Int {
        if (source.sampleRate == sampleRate) return source.read(buffer, maxFrames)
        var produced = 0
        while (produced < maxFrames) {
            val center = nextOut * step
            if (!ensure(floor(center).toLong() + TAPS + 1)) break
            val first = floor(center).toLong() - TAPS + 1
            for (t in 0 until 2 * TAPS) weights[t] = tableAt(abs(center - (first + t)))
            for (c in 0 until channelCount) buffer[produced * channelCount + c] = interpolate(c, first)
            produced++
            nextOut++
        }
        return produced
    }

    private fun interpolate(c: Int, first: Long): Float {
        var sum = 0f
        for (t in 0 until 2 * TAPS) {
            val j = first + t
            val i = (j - historyStart).toInt()
            if (j < 0 || i >= available) continue
            sum += history[c][i] * weights[t]
        }
        return sum
    }

    private fun tableAt(x: Double): Float {
        val position = x * RESOLUTION
        val index = position.toInt()
        if (index >= TAPS * RESOLUTION) return 0f
        val fraction = (position - index).toFloat()
        return table[index] + (table[index + 1] - table[index]) * fraction
    }

    private fun kernel(x: Double): Double {
        if (x == 0.0) return cutoff
        if (x >= TAPS) return 0.0
        val window = 0.42 + 0.5 * cos(PI * x / TAPS) + 0.08 * cos(2 * PI * x / TAPS)
        return sin(PI * cutoff * x) / (PI * x) * window
    }

    /** Garante entrada até o quadro [frame] (exclusive); falso se a música acabou antes de chegar perto. */
    private fun ensure(frame: Long): Boolean {
        dropOld()
        while (!ended && historyStart + available < frame) {
            val n = source.read(block, BLOCK)
            if (n == 0) ended = true else append(n)
        }
        return historyStart + available > floor(nextOut * step).toLong()
    }

    private fun append(frames: Int) {
        for (c in 0 until channelCount) {
            val grown = history[c].copyOf(available + frames)
            for (k in 0 until frames) grown[available + k] = block[k * channelCount + c]
            history[c] = grown
        }
        available += frames
    }

    private fun dropOld() {
        val keepFrom = floor(nextOut * step).toLong() - TAPS
        val drop = (keepFrom - historyStart).toInt()
        if (drop <= BLOCK) return
        for (c in 0 until channelCount) history[c] = history[c].copyOfRange(drop, available)
        available -= drop
        historyStart += drop
    }

    override fun close() = source.close()

    private companion object {
        const val TAPS = 32
        const val BLOCK = 4096
        const val RESOLUTION = 512
    }
}
