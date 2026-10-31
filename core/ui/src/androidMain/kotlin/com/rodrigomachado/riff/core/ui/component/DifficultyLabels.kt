package com.rodrigomachado.riff.core.ui.component

import androidx.annotation.StringRes
import com.rodrigomachado.riff.core.ui.R

/** Rótulo da dificuldade de índice [index] (0 = Fácil … 3 = Expert, a ordem de `Difficulty`). */
@StringRes
fun difficultyLabel(index: Int): Int = when (index) {
    0 -> R.string.difficulty_easy
    1 -> R.string.difficulty_medium
    2 -> R.string.difficulty_hard
    else -> R.string.difficulty_expert
}
