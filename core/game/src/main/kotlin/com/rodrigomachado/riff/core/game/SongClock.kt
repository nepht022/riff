package com.rodrigomachado.riff.core.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Fonte de tempo monotônica em ms. No Android, `SystemClock.elapsedRealtime()`. */
fun interface TimeSource {
    fun nowMs(): Long
}

/**
 * Relógio da música. Extrapola a última posição informada pelo player e corrige desvios pequenos
 * aos poucos, para as notas não pularem. Desvios grandes (o usuário mexeu na barra) e mudanças de
 * pausa são aplicados na hora.
 *
 * [offsetMs] é somado à posição. Valores negativos compensam atraso de áudio (fone Bluetooth).
 */
class SongClock(private val timeSource: TimeSource, offsetMs: Long = 0) {
    /** Ajuste somado à posição; pode mudar no meio da partida (ajuste fino na pausa). */
    var offsetMs: Long = offsetMs

    private var anchorPositionMs = 0L
    private var anchorAtMs = 0L
    private var paused = true
    private var synced = false
    private var residualMs = 0.0
    private var lastQueryAtMs = 0L

    val isPaused: Boolean get() = paused

    fun sync(positionMs: Long, isPaused: Boolean, capturedAtMs: Long) {
        val shownAtCapture = rawAt(capturedAtMs) + residualMs
        val drift = positionMs - shownAtCapture
        // Pausado, o tempo fica congelado: qualquer posição nova vale na hora, sem suavização.
        val hardReset = !synced || isPaused || isPaused != paused || abs(drift) >= HARD_RESET_THRESHOLD_MS
        anchorPositionMs = positionMs
        anchorAtMs = capturedAtMs
        paused = isPaused
        residualMs = if (hardReset) 0.0 else -drift
        synced = true
        lastQueryAtMs = timeSource.nowMs()
    }

    fun positionMs(): Long {
        val now = timeSource.nowMs()
        val step = (now - lastQueryAtMs).coerceAtLeast(0) * SLEW_RATE
        residualMs = if (residualMs > 0) max(0.0, residualMs - step) else min(0.0, residualMs + step)
        lastQueryAtMs = now
        return (rawAt(now) + residualMs).roundToLong() + offsetMs
    }

    private fun rawAt(timeMs: Long): Double =
        if (paused) anchorPositionMs.toDouble() else (anchorPositionMs + (timeMs - anchorAtMs)).toDouble()

    companion object {
        const val HARD_RESET_THRESHOLD_MS = 80

        /** Até 5 ms de correção a cada frame de 16,7 ms. */
        const val SLEW_RATE = 0.3
    }
}
