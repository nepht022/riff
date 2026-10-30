package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

/**
 * "Separar a guitarra com IA?" — depois da dificuldade, numa música sem guitarra separada. Voltar à esquerda; Sem
 * separar e Separar à direita, em destaque.
 */
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
                val message = when {
                    ask.queueWaiting -> R.string.library_separate_queue_waiting
                    ask.busy -> R.string.library_separate_busy
                    else -> R.string.library_separate_message
                }
                Text(stringResource(message))
                if (ask.lowMemory) Text(stringResource(R.string.library_separate_low_memory))
            }
        },
        confirmButton = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = withMenuSound(MenuSound.BACK, onDismiss)) {
                    Text(stringResource(R.string.library_back), color = RiffColors.Paper)
                }
                Spacer(Modifier.weight(1f))
                HighlightButton(stringResource(R.string.library_play_without_separating), onPlayWithout)
                HighlightButton(
                    stringResource(if (ask.busy) R.string.library_separate_queue else R.string.library_separate),
                    onSeparate,
                )
            }
        },
    )
}

/** Destaque compacto (vermelho do cartaz): cabem três ações na linha do diálogo, mesmo em tela estreita. */
@Composable
private fun HighlightButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = withMenuSound(MenuSound.CONFIRM, onClick),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = RiffColors.TourRed, contentColor = RiffColors.Paper),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
    ) { Text(text, maxLines = 1) }
}
