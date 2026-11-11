package com.rodrigomachado.riff.feature.library

import androidx.compose.runtime.Composable
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.ui.component.DifficultyDialog
import com.rodrigomachado.riff.core.ui.component.difficultyName

@Composable
internal fun DifficultyChoice(chart: Chart, onClose: () -> Unit, onChosen: (Chart, Difficulty) -> Unit) {
    DifficultyDialog(
        title = chart.title,
        options = chart.difficulties.sorted(),
        label = { difficultyName(it.ordinal) },
        onChoose = { difficulty ->
            onClose()
            onChosen(chart, difficulty)
        },
        onDismiss = onClose,
    )
}
