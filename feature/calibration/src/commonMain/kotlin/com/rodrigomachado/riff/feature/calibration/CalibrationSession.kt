package com.rodrigomachado.riff.feature.calibration

import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface CalibrationResult {
    /** Atraso médio do toque em relação ao clique (positivo: o jogador toca depois do clique). */
    data class Measured(val latencyMs: Int) : CalibrationResult

    data object Unstable : CalibrationResult
}

/** Compara os instantes dos cliques (no alto-falante) com os toques do jogador. */
object CalibrationSession {
    const val LEAD_IN = 4
    const val MEASURED = 20
    const val MIN_VALID = 12
    const val OUTLIER_MS = 250L

    fun result(clickTimesMs: List<Long>, tapTimesMs: List<Long>): CalibrationResult {
        if (clickTimesMs.isEmpty()) return CalibrationResult.Unstable
        // Cada clique fica só com o toque mais próximo; toques nos cliques de entrada não contam.
        val best = mutableMapOf<Int, Long>()
        for (tap in tapTimesMs) {
            val click = clickTimesMs.indices.minBy { abs(tap - clickTimesMs[it]) }
            if (click < LEAD_IN) continue
            val diff = tap - clickTimesMs[click]
            if (best[click]?.let { abs(it) <= abs(diff) } != true) best[click] = diff
        }
        val diffs = best.values.sorted()
        if (diffs.size < MIN_VALID) return CalibrationResult.Unstable
        val center = median(diffs)
        val kept = diffs.filter { abs(it - center) <= OUTLIER_MS }
        if (kept.size < MIN_VALID) return CalibrationResult.Unstable
        return CalibrationResult.Measured(median(kept).roundToInt())
    }

    private fun median(sorted: List<Long>): Double {
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle].toDouble() else (sorted[middle - 1] + sorted[middle]) / 2.0
    }
}
