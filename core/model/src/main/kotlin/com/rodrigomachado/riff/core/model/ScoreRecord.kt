package com.rodrigomachado.riff.core.model

data class ScoreRecord(
    val id: Long,
    val chartId: String,
    val difficulty: Difficulty,
    val score: Long,
    val accuracy: Float,
    val maxCombo: Int,
    val perfect: Int,
    val good: Int,
    val miss: Int,
    val playedAtEpochMs: Long,
    /** "Tempo sem errar": o maior trecho sem erro, em ms do mapa; `null` em partidas de antes da medida. */
    val cleanStreakMs: Long? = null,
)
