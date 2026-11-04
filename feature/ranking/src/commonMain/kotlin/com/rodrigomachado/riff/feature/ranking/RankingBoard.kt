package com.rodrigomachado.riff.feature.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.data.ranking.BoardLine
import com.rodrigomachado.riff.core.data.ranking.RankingMetric
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.difficultyName
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.ranking.resources.Res
import com.rodrigomachado.riff.feature.ranking.resources.ranking_ascending
import com.rodrigomachado.riff.feature.ranking.resources.ranking_descending
import com.rodrigomachado.riff.feature.ranking.resources.ranking_empty
import com.rodrigomachado.riff.feature.ranking.resources.ranking_loading
import com.rodrigomachado.riff.feature.ranking.resources.ranking_metric_accuracy
import com.rodrigomachado.riff.feature.ranking.resources.ranking_metric_clean
import com.rodrigomachado.riff.feature.ranking.resources.ranking_metric_combo
import com.rodrigomachado.riff.feature.ranking.resources.ranking_metric_perfect
import com.rodrigomachado.riff.feature.ranking.resources.ranking_metric_points
import com.rodrigomachado.riff.feature.ranking.resources.ranking_position
import com.rodrigomachado.riff.feature.ranking.resources.ranking_search_name
import com.rodrigomachado.riff.feature.ranking.resources.ranking_you
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.text.NumberFormat
import java.util.Locale

/** O que o placar pede à tela. */
class BoardActions(
    val onDifficulty: (Difficulty) -> Unit = {},
    val onMetric: (RankingMetric) -> Unit = {},
    val onToggleDirection: () -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
fun RankingBoardScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: RankingBoardViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RankingBoardContent(
        state,
        BoardActions(
            viewModel::onDifficulty,
            viewModel::onMetric,
            viewModel::onToggleDirection,
            viewModel::onQuery,
            { viewModel.load() },
            onBack,
        ),
        modifier,
    )
}

/** O placar de um mapa (maquete E): dificuldade, número, sentido, busca por nome e você destacado. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RankingBoardContent(state: RankingBoardUiState, actions: BoardActions, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item { BackButton(actions.onBack) }
        item { PosterTitle(state.title, style = MaterialTheme.typography.headlineSmall) }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
                verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
            ) {
                for (difficulty in state.board?.difficulties.orEmpty()) {
                    Chip(difficultyName(difficulty.ordinal), difficulty == state.board?.difficulty) {
                        actions.onDifficulty(difficulty)
                    }
                }
            }
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
                verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
            ) {
                for (metric in RankingMetric.entries) {
                    Chip(stringResource(metric.label()), metric == state.metric) { actions.onMetric(metric) }
                }
                val direction = if (state.descending) Res.string.ranking_descending else Res.string.ranking_ascending
                Chip(
                    stringResource(direction),
                    selected = true,
                    onClick = actions.onToggleDirection,
                )
            }
        }
        item { SearchField(state.query, stringResource(Res.string.ranking_search_name), actions.onQuery) }
        if (state.offline) item { Offline(actions.onRetry) }
        val lines = state.board?.lines.orEmpty()
        if (state.loading && state.board == null) item { Message(stringResource(Res.string.ranking_loading)) }
        if (!state.offline && !state.loading && lines.isEmpty()) {
            item {
                Message(
                    stringResource(Res.string.ranking_empty),
                )
            }
        }
        items(lines, key = { it.position to it.name }) { line -> Line(line, state.metric) }
    }
}

@Composable
private fun Line(line: BoardLine, metric: RankingMetric) {
    val shape = MaterialTheme.shapes.small
    Row(
        Modifier.fillMaxWidth()
            .background(if (line.you) RiffColors.TourRed.copy(alpha = YOU_ALPHA) else Color.Transparent, shape)
            .border(1.dp, if (line.you) RiffColors.TourRed else Color.Transparent, shape)
            .padding(horizontal = Spacing.sm, vertical = LINE_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.ranking_position, line.position),
            Modifier.width(POSITION_WIDTH),
            style = MaterialTheme.typography.titleMedium,
            color = RiffColors.PaperDim,
        )
        Text(
            if (line.you) stringResource(Res.string.ranking_you, line.name) else line.name,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = RiffColors.Paper,
        )
        Text(
            formatValue(metric, line.value, line.outOf, currentLocale()),
            style = MaterialTheme.typography.titleMedium,
            color = RiffColors.Paper,
        )
    }
}

/** Pontos agrupados, precisão em %, tempo sem errar em m:ss, perfeitos "de quantos" (70/85), o resto inteiro. */
internal fun formatValue(
    metric: RankingMetric,
    value: Double,
    outOf: Int? = null,
    locale: Locale = Locale.forLanguageTag("pt-BR"),
): String = when (metric) {
    RankingMetric.POINTS -> NumberFormat.getIntegerInstance(locale).format(value.toLong())
    RankingMetric.ACCURACY -> NumberFormat.getPercentInstance(locale).format(value)
    RankingMetric.CLEAN -> (value.toLong() / MS_PER_SECOND).let { "%d:%02d".format(it / SECONDS, it % SECONDS) }
    RankingMetric.PERFECT -> value.toLong().toString() + outOf?.let { "/$it" }.orEmpty()
    RankingMetric.COMBO -> value.toLong().toString()
}

private fun RankingMetric.label(): StringResource = when (this) {
    RankingMetric.POINTS -> Res.string.ranking_metric_points
    RankingMetric.ACCURACY -> Res.string.ranking_metric_accuracy
    RankingMetric.COMBO -> Res.string.ranking_metric_combo
    RankingMetric.PERFECT -> Res.string.ranking_metric_perfect
    RankingMetric.CLEAN -> Res.string.ranking_metric_clean
}

private const val YOU_ALPHA = 0.25f
private const val MS_PER_SECOND = 1_000L
private const val SECONDS = 60
private val LINE_PADDING = 10.dp
private val POSITION_WIDTH = 44.dp
