package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.library.resources.Res
import com.rodrigomachado.riff.feature.library.resources.library_back
import com.rodrigomachado.riff.feature.library.resources.library_play_without_separating
import com.rodrigomachado.riff.feature.library.resources.library_separate
import com.rodrigomachado.riff.feature.library.resources.library_separate_busy
import com.rodrigomachado.riff.feature.library.resources.library_separate_low_memory
import com.rodrigomachado.riff.feature.library.resources.library_separate_message
import com.rodrigomachado.riff.feature.library.resources.library_separate_queue
import com.rodrigomachado.riff.feature.library.resources.library_separate_queue_waiting
import com.rodrigomachado.riff.feature.library.resources.library_separate_title
import org.jetbrains.compose.resources.stringResource

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
        title = { Text(stringResource(Res.string.library_separate_title)) },
        text = {
            Column {
                val message = when {
                    ask.queueWaiting -> Res.string.library_separate_queue_waiting
                    ask.busy -> Res.string.library_separate_busy
                    else -> Res.string.library_separate_message
                }
                Text(stringResource(message))
                if (ask.lowMemory) Text(stringResource(Res.string.library_separate_low_memory))
            }
        },
        confirmButton = {
            // No celular o diálogo é estreito: o que não cabe desce inteiro para a linha de baixo, à direita.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = withMenuSound(MenuSound.BACK, onDismiss)) {
                    Text(stringResource(Res.string.library_back), color = RiffColors.Paper, softWrap = false)
                }
                HighlightButton(stringResource(Res.string.library_play_without_separating), onPlayWithout)
                HighlightButton(
                    stringResource(if (ask.busy) Res.string.library_separate_queue else Res.string.library_separate),
                    onSeparate,
                )
            }
        },
    )
}

/** Destaque compacto (vermelho do cartaz), com o texto sempre inteiro. */
@Composable
private fun HighlightButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = withMenuSound(MenuSound.CONFIRM, onClick),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = RiffColors.TourRed, contentColor = RiffColors.Paper),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
    ) { Text(text, softWrap = false) }
}
