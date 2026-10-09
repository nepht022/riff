package com.rodrigomachado.riff.core.audio

import kotlin.random.Random

/** Variação do som de erro a tocar e com que força (0,75 a 1). */
data class MissChoice(val index: Int, val gain: Float)

/**
 * Escolhe o som de cada erro, como no Guitar Hero: sorteia uma das [count] variações sem repetir a anterior,
 * com força um pouco diferente a cada vez; erros a menos de [MIN_GAP_MS] do último som não tocam outro, para
 * uma rajada de erros soar como uma sequência e não como sons empilhados.
 */
class MissSoundPicker(private val count: Int, private val random: Random = Random.Default) {
    private var last = -1
    private var lastAtMs = Long.MIN_VALUE

    /** `null` quando o erro vem cedo demais e não deve tocar som. */
    fun pick(nowMs: Long): MissChoice? {
        if (lastAtMs != Long.MIN_VALUE && nowMs - lastAtMs < MIN_GAP_MS) return null
        val index = when {
            count == 1 -> 0
            last < 0 -> random.nextInt(count)
            else -> random.nextInt(count - 1).let { if (it >= last) it + 1 else it }
        }
        last = index
        lastAtMs = nowMs
        return MissChoice(index, MIN_GAIN + random.nextFloat() * (1f - MIN_GAIN))
    }

    companion object {
        const val MIN_GAP_MS = 250L
        private const val MIN_GAIN = 0.75f
    }
}
