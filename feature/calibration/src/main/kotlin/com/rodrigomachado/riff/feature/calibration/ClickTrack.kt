package com.rodrigomachado.riff.feature.calibration

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.tanh
import kotlin.random.Random

/** Metrônomo da calibração: 4 batidas de entrada e 20 medidas, a 100 BPM, sintetizadas aqui. */
object ClickTrack {
    const val INTERVAL_MS = 600
    const val START_DELAY_MS = 500
    val CLICK_COUNT = CalibrationSession.LEAD_IN + CalibrationSession.MEASURED
    private const val TAIL_MS = 500

    /** Quadro (desde o início da saída) em que cada batida começa. */
    fun clickFrames(sampleRate: Int): LongArray = LongArray(CLICK_COUNT) {
        ((START_DELAY_MS + it * INTERVAL_MS) * sampleRate / 1_000.0).roundToLong()
    }

    /** O metrônomo inteiro em PCM float estéreo intercalado. */
    fun render(sampleRate: Int): FloatArray {
        val frames = clickFrames(sampleRate)
        val total = (frames.last() + (TAIL_MS * sampleRate / 1_000)).toInt()
        val pcm = FloatArray(total * 2)
        val note = GuitarNote.render(sampleRate)
        for (start in frames) {
            for (i in note.indices) {
                val frame = (start + i).toInt()
                pcm[frame * 2] = note[i]
                pcm[frame * 2 + 1] = note[i]
            }
        }
        return pcm
    }
}

/**
 * Uma nota de guitarra com distorção leve, o mesmo timbre do som de acerto (`scripts/gerar_sfx.py`): corda
 * Karplus-Strong em Mi3, "crunch" e caixa de amplificador. O ataque é imediato, para o instante da batida ser claro.
 */
internal object GuitarNote {
    private const val FREQ_HZ = 164.81
    private const val SECONDS = 0.25
    private const val FADE_SECONDS = 0.06
    private const val ATTACK_SECONDS = 0.001
    private const val DRIVE = 2.5
    private const val DAMPING = 0.998 * 0.5
    private const val SEED = 60
    private const val PEAK = 0.6f

    fun render(rate: Int): FloatArray {
        var samples = pluck(rate)
        samples = highpass(samples, 120.0, rate)
        samples = DoubleArray(samples.size) { tanh(DRIVE * samples[it]) }
        samples = highpass(lowpass(lowpass(samples, 4_000.0, rate), 5_500.0, rate), 70.0, rate)
        val attack = ATTACK_SECONDS * rate
        val fade = FADE_SECONDS * rate
        val count = samples.size
        val shaped = DoubleArray(count) { samples[it] * min(1.0, it / attack) * min(1.0, (count - it) / fade) }
        val peak = shaped.maxOf { abs(it) }
        return FloatArray(count) { (shaped[it] / peak).toFloat() * PEAK }
    }

    private fun pluck(rate: Int): DoubleArray {
        val random = Random(SEED)
        val period = (rate / FREQ_HZ).toInt()
        val delay = DoubleArray(period) { random.nextDouble() * 2 - 1 }
        return DoubleArray((rate * SECONDS).toInt()) { i ->
            val j = i % period
            val value = delay[j]
            delay[j] = DAMPING * (value + delay[(j + 1) % period])
            value
        }
    }

    private fun lowpass(samples: DoubleArray, cutoff: Double, rate: Int): DoubleArray {
        val alpha = 1 - exp(-2 * Math.PI * cutoff / rate)
        var y = 0.0
        return DoubleArray(samples.size) {
            y += alpha * (samples[it] - y)
            y
        }
    }

    private fun highpass(samples: DoubleArray, cutoff: Double, rate: Int): DoubleArray {
        val low = lowpass(samples, cutoff, rate)
        return DoubleArray(samples.size) { samples[it] - low[it] }
    }
}

/**
 * Converte os quadros dos cliques em instantes do relógio do sistema, a partir de leituras de "agora, a saída
 * já tocou N quadros". Cada leitura com a saída andando dá uma estimativa; o instante é a média delas.
 */
class ClickTimeline(private val sampleRate: Int, private val clickFrames: LongArray) {
    private val sums = DoubleArray(clickFrames.size)
    private var samples = 0
    private var lastPlayed = -1L

    fun observe(nowMs: Long, playedFrames: Long) {
        // Saída parada (começo, buffer cheio): a leitura não diz nada novo sobre o relógio.
        if (playedFrames <= 0 || playedFrames == lastPlayed) return
        lastPlayed = playedFrames
        for (i in clickFrames.indices) sums[i] += nowMs + (clickFrames[i] - playedFrames) * 1_000.0 / sampleRate
        samples++
    }

    fun clickTimesMs(): List<Long> = if (samples == 0) emptyList() else sums.map { (it / samples).roundToLong() }
}
