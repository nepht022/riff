package com.rodrigomachado.riff.core.audio

/**
 * Mistura faixas em estéreo intercalado. Cada faixa tem um ganho alvo; a mudança vira uma rampa linear de
 * [rampFrames] quadros, para o corte da guitarra não estalar. [mix] não aloca nada: roda a cada bloco de áudio.
 * Os ganhos podem mudar de outra thread (o toque do jogador); o pior caso é a rampa começar um bloco depois.
 */
class StemMixer(private val trackCount: Int, private val rampFrames: Int) {
    private val target = FloatArray(trackCount) { 1f }
    private val current = FloatArray(trackCount) { 1f }

    /** Volume geral do app, aplicado à mistura inteira. */
    @Volatile
    var masterGain = 1f

    fun setTargetGain(track: Int, gain: Float) {
        target[track] = gain.coerceIn(0f, 1f)
    }

    fun currentGain(track: Int): Float = current[track]

    /**
     * @param inputs amostras intercaladas de cada faixa, com [channels] canais (1 ou 2)
     * @param available quadros válidos de cada faixa neste bloco; depois deles a faixa conta como silêncio
     * @param out saída estéreo intercalada; só as primeiras `frames * 2` posições são escritas
     */
    fun mix(inputs: Array<FloatArray>, channels: IntArray, available: IntArray, frames: Int, out: FloatArray) {
        java.util.Arrays.fill(out, 0, frames * 2, 0f)
        val step = 1f / rampFrames
        for (track in 0 until trackCount) {
            val input = inputs[track]
            val count = channels[track]
            val valid = minOf(available[track], frames)
            val goal = target[track]
            var gain = current[track]
            for (frame in 0 until frames) {
                gain = approach(gain, goal, step)
                if (frame < valid) {
                    val left = input[frame * count]
                    val right = if (count == 2) input[frame * count + 1] else left
                    out[frame * 2] += left * gain
                    out[frame * 2 + 1] += right * gain
                }
            }
            current[track] = gain
        }
        val master = masterGain
        for (i in 0 until frames * 2) out[i] = (out[i] * master).coerceIn(-1f, 1f)
    }

    private fun approach(value: Float, goal: Float, step: Float): Float = when {
        value < goal -> minOf(goal, value + step)
        value > goal -> maxOf(goal, value - step)
        else -> value
    }

    companion object {
        const val RAMP_MS = 30

        fun rampFrames(sampleRate: Int): Int = sampleRate * RAMP_MS / 1_000
    }
}
