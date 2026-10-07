package com.rodrigomachado.riff.core.model

enum class Judgment { PERFECT, GOOD, MISS }

/** Fotografia do estado da partida, para a UI desenhar. */
data class GameState(
    val songTimeMs: Long,
    val score: Long,
    val combo: Int,
    val maxCombo: Int,
    val multiplier: Int,
    val counts: Map<Judgment, Int>,
    val activeSustains: Set<Int>,
)
