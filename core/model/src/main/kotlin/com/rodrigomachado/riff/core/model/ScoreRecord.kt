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
)
