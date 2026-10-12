package com.rodrigomachado.riff.feature.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.difficultyLabel
import com.rodrigomachado.riff.core.ui.stage.ScoreText
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import java.text.NumberFormat

@Composable
fun ResultsScreen(
    onPlayAgain: (chartId: String, difficulty: Difficulty) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ResultsContent(state, onPlayAgain, onDone, modifier)
}

@Composable
fun ResultsContent(
    state: ResultsUiState,
    onPlayAgain: (chartId: String, difficulty: Difficulty) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        ResultsUiState.Loading -> LoadingState(modifier)

        ResultsUiState.Missing -> EmptyState(
            title = stringResource(R.string.results_missing),
            message = "",
            actionLabel = stringResource(R.string.results_back),
            onAction = onDone,
            modifier = modifier,
        )

        is ResultsUiState.Ready -> ReadyResults(state, onPlayAgain, onDone, modifier)
    }
}

@Composable
private fun ReadyResults(
    state: ResultsUiState.Ready,
    onPlayAgain: (chartId: String, difficulty: Difficulty) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier,
) {
    val record = state.record
    val locale = LocalConfiguration.current.locales[0]
    val score = remember(record.score, locale) { NumberFormat.getIntegerInstance(locale).format(record.score) }
    val accuracy = remember(record.accuracy, locale) { NumberFormat.getPercentInstance(locale).format(record.accuracy) }
    val summary = stringResource(R.string.results_summary, score, accuracy) +
        if (state.isNewBest) " " + stringResource(R.string.results_new_best) else ""
    Column(
        modifier
            .fillMaxSize()
            // O TalkBack lê o título do painel quando a tela aparece: o resultado é anunciado sem tocar em nada.
            .semantics { paneTitle = summary }
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        PosterTitle(state.title, style = MaterialTheme.typography.headlineMedium)
        Text(state.artist, style = MaterialTheme.typography.titleMedium, color = RiffColors.PaperDim)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusBadge(stringResource(difficultyLabel(record.difficulty.ordinal)), BadgeTone.NEUTRAL)
            if (state.isNewBest) StatusBadge(stringResource(R.string.results_new_best), BadgeTone.SUCCESS)
        }
        ScoreText(record.score)
        Column(
            Modifier.fillMaxWidth().background(RiffColors.Paper, MaterialTheme.shapes.small).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Stat(
                stringResource(R.string.results_accuracy),
                NumberFormat.getPercentInstance(locale).format(record.accuracy),
            )
            Stat(stringResource(R.string.results_max_combo), record.maxCombo.toString())
            Stat(stringResource(R.string.results_perfect), record.perfect.toString())
            Stat(stringResource(R.string.results_good), record.good.toString())
            Stat(stringResource(R.string.results_miss), record.miss.toString())
        }
        PosterButton(
            stringResource(R.string.results_play_again),
            onClick = { onPlayAgain(record.chartId, record.difficulty) },
        )
        TextButton(
            onClick = onDone,
        ) { Text(stringResource(R.string.results_back), color = RiffColors.Paper) }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = RiffColors.Ink)
        Text(value, style = MaterialTheme.typography.titleMedium, color = RiffColors.Ink)
    }
}
