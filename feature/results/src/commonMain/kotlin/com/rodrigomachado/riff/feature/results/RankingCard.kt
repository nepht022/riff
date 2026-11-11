package com.rodrigomachado.riff.feature.results

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.data.ranking.SubmitStatus
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.results.resources.Res
import com.rodrigomachado.riff.feature.results.resources.results_ranking_no_name
import com.rodrigomachado.riff.feature.results.resources.results_ranking_position
import com.rodrigomachado.riff.feature.results.resources.results_ranking_queued
import com.rodrigomachado.riff.feature.results.resources.results_ranking_sending
import com.rodrigomachado.riff.feature.results.resources.results_see_ranking
import org.jetbrains.compose.resources.stringResource

/**
 * A linha do ranking: o que aconteceu com a partida (a posição, o envio, sem internet) e "Ver ranking" ao lado; sem
 * nome, o convite para escolher um. Com a fonte grande, o "Ver ranking" desce para baixo do texto.
 */
@Composable
internal fun RankingCard(status: SubmitStatus?, onSeeRanking: () -> Unit, onChooseName: () -> Unit) {
    val frame = Modifier.fillMaxWidth().border(1.dp, RiffColors.PaperDim, MaterialTheme.shapes.small)
    if (status == SubmitStatus.NoName) {
        Box(frame.padding(Spacing.xs)) {
            TextButton(
                onClick = withMenuSound(MenuSound.NAVIGATE, onChooseName),
                modifier = Modifier.focusHighlight(CircleShape),
            ) {
                Text(
                    stringResource(Res.string.results_ranking_no_name),
                    color = RiffColors.Paper,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        return
    }
    FlowRow(
        frame.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        line(status)?.let {
            Text(
                it,
                Modifier.align(Alignment.CenterVertically).padding(end = Spacing.sm),
                style = MaterialTheme.typography.titleMedium,
                color = RiffColors.Paper,
            )
        }
        TextButton(
            onClick = withMenuSound(MenuSound.NAVIGATE, onSeeRanking),
            modifier = Modifier.align(Alignment.CenterVertically).focusHighlight(CircleShape),
        ) {
            Text(stringResource(Res.string.results_see_ranking), color = RiffColors.Paper, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun line(status: SubmitStatus?): String? = when (status) {
    is SubmitStatus.Placed -> stringResource(Res.string.results_ranking_position, status.position, status.total)
    SubmitStatus.Sending -> stringResource(Res.string.results_ranking_sending)
    SubmitStatus.Queued -> stringResource(Res.string.results_ranking_queued)
    SubmitStatus.NoName, SubmitStatus.NotBest, null -> null
}
