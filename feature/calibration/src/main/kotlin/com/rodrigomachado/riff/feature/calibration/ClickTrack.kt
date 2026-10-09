package com.rodrigomachado.riff.feature.calibration

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToLong
import kotlin.math.sin

/** Metrônomo da calibração: 4 cliques de entrada e 20 medidos, a 100 BPM, sintetizados aqui. */
object ClickTrack {
    const val INTERVAL_MS = 600
    const val START_DELAY_MS = 500
    val CLICK_COUNT = CalibrationSession.LEAD_IN + CalibrationSession.MEASURED
    private const val CLICK_MS = 20
    private const val CLICK_HZ = 1_500.0
    private const val DECAY_MS = 4.0
    private const val LEVEL = 0.6f
    private const val TAIL_MS = 500

    /** Quadro (desde o início da saída) em que cada clique começa. */
    fun clickFrames(sampleRate: Int): LongArray = LongArray(CLICK_COUNT) {
        ((START_DELAY_MS + it * INTERVAL_MS) * sampleRate / 1_000.0).roundToLong()
    }

    /** O metrônomo inteiro em PCM float estéreo intercalado. */
    fun render(sampleRate: Int): FloatArray {
        val frames = clickFrames(sampleRate)
        val total = (frames.last() + (TAIL_MS * sampleRate / 1_000)).toInt()
        val pcm = FloatArray(total * 2)
        val length = CLICK_MS * sampleRate / 1_000
        for (start in frames) {
            for (i in 0 until length) {
                val t = i.toDouble() / sampleRate
                val sample = (sin(2 * PI * CLICK_HZ * t) * exp(-t * 1_000 / DECAY_MS)).toFloat() * LEVEL
                val frame = (start + i).toInt()
                pcm[frame * 2] = sample
                pcm[frame * 2 + 1] = sample
            }
        }
        return pcm
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
