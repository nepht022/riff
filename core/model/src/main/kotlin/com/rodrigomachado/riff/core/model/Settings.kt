package com.rodrigomachado.riff.core.model

/**
 * Preferências do jogador. [audioOffsetMs] negativo compensa atraso de áudio (fone Bluetooth).
 * [sfxVolume] é o volume dos sons de acerto e erro, de 0 (desligado) a 1.
 */
data class Settings(
    val audioOffsetMs: Int,
    val noteSpeed: Float,
    val leftHanded: Boolean,
    val defaultDifficulty: Difficulty,
    val sfxVolume: Float,
) {
    init {
        require(sfxVolume in 0f..1f) { "sfxVolume fora de 0..1: $sfxVolume" }
    }

    companion object {
        val DEFAULT = Settings(
            audioOffsetMs = 0,
            noteSpeed = 1f,
            leftHanded = false,
            defaultDifficulty = Difficulty.MEDIUM,
            sfxVolume = 0.6f,
        )
    }
}
