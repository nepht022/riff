package com.rodrigomachado.riff.core.model

/** A separação da guitarra pela IA em segundo plano: o ícone, o painel e a notificação leem isto. */
sealed interface SeparationStatus {
    data object Idle : SeparationStatus

    /** [progress] de 0 a 1; [remainingMs] nulo enquanto não dá para estimar. */
    data class Running(
        val chartId: String,
        val title: String,
        val progress: Float,
        val remainingMs: Long?,
        val pausedForHeat: Boolean = false,
    ) : SeparationStatus

    data class Failed(val title: String, val reason: SeparationFailure) : SeparationStatus
}

enum class SeparationFailure { NO_SPACE, NO_AUDIO, ERROR }

/** Músicas separadas pela IA e o espaço que ocupam. */
data class SeparationUsage(val songs: Int, val bytes: Long)
