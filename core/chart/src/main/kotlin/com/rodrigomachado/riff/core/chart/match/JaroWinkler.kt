package com.rodrigomachado.riff.core.chart.match

import kotlin.math.max
import kotlin.math.min

/** Similaridade Jaro-Winkler entre 0 (nada em comum) e 1 (iguais). */
object JaroWinkler {
    private const val PREFIX_SCALE = 0.1
    private const val MAX_PREFIX = 4

    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val aMatched = BooleanArray(a.length)
        val bMatched = BooleanArray(b.length)
        val matches = markMatches(a, b, aMatched, bMatched)
        if (matches == 0) return 0.0
        val m = matches.toDouble()
        val transpositions = countHalfTranspositions(a, b, aMatched, bMatched) / 2.0
        val jaro = (m / a.length + m / b.length + (m - transpositions) / m) / 3
        val prefix = a.zip(b).takeWhile { it.first == it.second }.size.coerceAtMost(MAX_PREFIX)
        return jaro + prefix * PREFIX_SCALE * (1 - jaro)
    }

    /** Caracteres iguais dentro da janela de busca; marca quais foram casados em cada texto. */
    private fun markMatches(a: String, b: String, aMatched: BooleanArray, bMatched: BooleanArray): Int {
        val window = (max(a.length, b.length) / 2 - 1).coerceAtLeast(0)
        var matches = 0
        for (i in a.indices) {
            for (j in max(0, i - window) until min(i + window + 1, b.length)) {
                if (!bMatched[j] && a[i] == b[j]) {
                    aMatched[i] = true
                    bMatched[j] = true
                    matches++
                    break
                }
            }
        }
        return matches
    }

    /** Casados fora de ordem; cada transposição conta duas vezes. */
    private fun countHalfTranspositions(a: String, b: String, aMatched: BooleanArray, bMatched: BooleanArray): Int {
        var count = 0
        var k = 0
        for (i in a.indices) {
            if (!aMatched[i]) continue
            while (!bMatched[k]) k++
            if (a[i] != b[k]) count++
            k++
        }
        return count
    }
}
