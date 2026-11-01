package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.DifficultyDialog
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.difficultyName
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.library.resources.Res
import com.rodrigomachado.riff.feature.library.resources.library_ai_separated
import com.rodrigomachado.riff.feature.library.resources.library_change_folder
import com.rodrigomachado.riff.feature.library.resources.library_rescan
import com.rodrigomachado.riff.feature.library.resources.library_select
import com.rodrigomachado.riff.feature.library.resources.library_separate_guitar
import org.jetbrains.compose.resources.stringResource

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

/** Carimbos do mapa: a dificuldade mais alta e, se for o caso, a guitarra separada pela IA. */
@Composable
internal fun ChartBadges(chart: Chart, aiSeparated: Boolean) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        chart.difficulties.maxOrNull()?.let {
            StatusBadge(difficultyName(it.ordinal), BadgeTone.NEUTRAL)
        }
        when {
            chart.separateGuitar -> StatusBadge(stringResource(Res.string.library_separate_guitar), BadgeTone.SUCCESS)
            aiSeparated -> StatusBadge(stringResource(Res.string.library_ai_separated), BadgeTone.SUCCESS)
        }
    }
}

/** Reimportar, Trocar pasta e Selecionar (para excluir vários). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ListActions(onRescan: () -> Unit, onPickFolder: () -> Unit, onSelect: () -> Unit) {
    // Em tela estreita, o que não cabe desce para a linha de baixo (em vez de quebrar a palavra).
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        PosterButton(stringResource(Res.string.library_rescan), onClick = onRescan, sound = MenuSound.NAVIGATE)
        TextButton(onClick = withMenuSound(MenuSound.NAVIGATE, onPickFolder)) {
            Text(stringResource(Res.string.library_change_folder), color = RiffColors.Paper, maxLines = 1)
        }
        // Encostado à direita, alinhado com o Pesquisar do cabeçalho.
        Spacer(Modifier.weight(1f))
        TextButton(onClick = withMenuSound(MenuSound.NAVIGATE, onSelect)) {
            Text(stringResource(Res.string.library_select), color = RiffColors.Paper, maxLines = 1)
        }
    }
}
