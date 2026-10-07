package com.rodrigomachado.riff.core.model

/** Preferências do jogador. [audioOffsetMs] negativo compensa atraso de áudio (fone Bluetooth). */
data class Settings(
    val audioOffsetMs: Int,
    val noteSpeed: Float,
    val leftHanded: Boolean,
    val defaultDifficulty: Difficulty,
) {
    companion object {
        val DEFAULT = Settings(
            audioOffsetMs = 0,
            noteSpeed = 1f,
            leftHanded = false,
            defaultDifficulty = Difficulty.MEDIUM,
        )
    }
}
