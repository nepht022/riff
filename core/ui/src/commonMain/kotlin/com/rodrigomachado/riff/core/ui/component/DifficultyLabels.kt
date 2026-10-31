package com.rodrigomachado.riff.core.ui.component

import androidx.compose.runtime.Composable
import com.rodrigomachado.riff.core.ui.resources.Res
import com.rodrigomachado.riff.core.ui.resources.difficulty_easy
import com.rodrigomachado.riff.core.ui.resources.difficulty_expert
import com.rodrigomachado.riff.core.ui.resources.difficulty_hard
import com.rodrigomachado.riff.core.ui.resources.difficulty_medium
import org.jetbrains.compose.resources.stringResource

/** Nome da dificuldade de índice [index] (0 = Fácil … 3 = Expert, a ordem de `Difficulty`), no idioma do app. */
@Composable
fun difficultyName(index: Int): String = stringResource(
    when (index) {
        0 -> Res.string.difficulty_easy
        1 -> Res.string.difficulty_medium
        2 -> Res.string.difficulty_hard
        else -> Res.string.difficulty_expert
    },
)
