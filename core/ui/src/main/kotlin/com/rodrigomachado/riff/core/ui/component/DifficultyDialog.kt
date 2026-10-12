package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.window.Dialog
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.R
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

/** Etiquetas para testes e para os percursos automáticos de desempenho (`:baselineprofile`). */
object DifficultyDialogTestTags {
    /** Todas as opções; a primeira é a de menor dificuldade. */
    const val OPTION = "difficulty_option"
}

/** Escolha da dificuldade antes da partida. Usado na biblioteca e em "Tocando agora". */
@Composable
fun <T> DifficultyDialog(
    title: String,
    options: List<T>,
    label: @Composable (T) -> String,
    onChoose: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .background(RiffColors.StageBlack, MaterialTheme.shapes.large)
                .padding(Spacing.lg)
                // O diálogo é outra janela: a propriedade da raiz do app não chega aqui.
                .semantics { testTagsAsResourceId = true },
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = RiffColors.Paper)
            Text(
                stringResource(R.string.ui_choose_difficulty),
                style = MaterialTheme.typography.bodyMedium,
                color = RiffColors.PaperDim,
            )
            options.forEach { option ->
                PosterButton(
                    label(option),
                    onClick = { onChoose(option) },
                    sound = MenuSound.CONFIRM,
                    modifier = Modifier.testTag(DifficultyDialogTestTags.OPTION),
                )
            }
            TextButton(
                onClick = withMenuSound(MenuSound.BACK, onDismiss),
            ) { Text(stringResource(R.string.ui_cancel), color = RiffColors.Paper) }
        }
    }
}
