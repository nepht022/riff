package com.rodrigomachado.riff.core.ui.component

/** A nota do Resultado pela precisão da partida (de 0 a 1): S, A, B ou C. */
enum class ResultGrade {
    S,
    A,
    B,
    C,
    ;

    companion object {
        /** Os limites (inclusive) de cada nota; abaixo de [B_MIN] é C. */
        const val S_MIN = 0.98f
        const val A_MIN = 0.90f
        const val B_MIN = 0.80f

        fun of(accuracy: Float): ResultGrade = when {
            accuracy >= S_MIN -> S
            accuracy >= A_MIN -> A
            accuracy >= B_MIN -> B
            else -> C
        }
    }
}
