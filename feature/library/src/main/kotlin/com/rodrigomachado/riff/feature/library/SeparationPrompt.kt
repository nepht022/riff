package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors

/** "Separar a guitarra com IA?" — depois da dificuldade, numa música sem guitarra separada. */
@Composable
internal fun SeparationPrompt(
    ask: SeparationAsk,
    onSeparate: () -> Unit,
    onPlayWithout: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_separate_title)) },
        text = {
            Column {
                Text(
                    stringResource(if (ask.busy) R.string.library_separate_busy else R.string.library_separate_message),
                )
                if (ask.lowMemory && !ask.busy) Text(stringResource(R.string.library_separate_low_memory))
            }
        },
        confirmButton = {
            if (!ask.busy) {
                TextButton(onClick = withMenuSound(MenuSound.CONFIRM, onSeparate)) {
                    Text(stringResource(R.string.library_separate), color = RiffColors.Paper)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = withMenuSound(MenuSound.CONFIRM, onPlayWithout)) {
                Text(stringResource(R.string.library_play_without_separating), color = RiffColors.Paper)
            }
        },
    )
}
