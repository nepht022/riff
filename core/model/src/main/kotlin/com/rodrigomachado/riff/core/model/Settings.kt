package com.rodrigomachado.riff.core.model

/**
 * Preferências do jogador. [audioOffsetMs] negativo compensa atraso de áudio (fone Bluetooth).
 * Volumes vão de 0 (desligado) a 1: [masterVolume] multiplica todos os sons do app (não o Spotify, cujo
 * volume o SDK não controla); [hitVolume] e [missVolume] são os sons de acerto e de erro.
 */
data class Settings(
    val audioOffsetMs: Int,
    val noteSpeed: Float,
    val leftHanded: Boolean,
    val masterVolume: Float,
    val hitVolume: Float,
    val missVolume: Float,
) {
    init {
        require(noteSpeed in MIN_NOTE_SPEED..MAX_NOTE_SPEED) { "noteSpeed fora de $MIN_NOTE_SPEED..$MAX_NOTE_SPEED" }
        require(masterVolume in 0f..1f) { "masterVolume fora de 0..1: $masterVolume" }
        require(hitVolume in 0f..1f) { "hitVolume fora de 0..1: $hitVolume" }
        require(missVolume in 0f..1f) { "missVolume fora de 0..1: $missVolume" }
    }

    val effectiveHitVolume: Float get() = masterVolume * hitVolume

    val effectiveMissVolume: Float get() = masterVolume * missVolume

    companion object {
        const val MIN_NOTE_SPEED = 0.5f
        const val MAX_NOTE_SPEED = 2f

        val DEFAULT = Settings(
            audioOffsetMs = 0,
            noteSpeed = 1f,
            leftHanded = false,
            masterVolume = 1f,
            hitVolume = 0.6f,
            missVolume = 0.6f,
        )
    }
}
