package com.rodrigomachado.riff.feature.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.difficultyName
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.stage.ScoreText
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.results.resources.Res
import com.rodrigomachado.riff.feature.results.resources.results_accuracy
import com.rodrigomachado.riff.feature.results.resources.results_back
import com.rodrigomachado.riff.feature.results.resources.results_back_to_room
import com.rodrigomachado.riff.feature.results.resources.results_clean_time
import com.rodrigomachado.riff.feature.results.resources.results_good
import com.rodrigomachado.riff.feature.results.resources.results_max_combo
import com.rodrigomachado.riff.feature.results.resources.results_miss
import com.rodrigomachado.riff.feature.results.resources.results_missing
import com.rodrigomachado.riff.feature.results.resources.results_new_best
import com.rodrigomachado.riff.feature.results.resources.results_new_best_spoken
import com.rodrigomachado.riff.feature.results.resources.results_perfect
import com.rodrigomachado.riff.feature.results.resources.results_play_again
import com.rodrigomachado.riff.feature.results.resources.results_see_room
import com.rodrigomachado.riff.feature.results.resources.results_summary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.text.NumberFormat

/**
 * O Resultado; numa partida de sala ([inRoom], da [ResultsRoute]), sem "Jogar de novo": "Ver a sala" ([onSeeRoom], o
 * Resultado da sala, que o app abre) e "Voltar à sala" ([onDone]).
 */
@Composable
fun ResultsScreen(
    onPlayAgain: (chartId: String, difficulty: Difficulty) -> Unit,
    onDone: () -> Unit,
    viewModel: ResultsViewModel,
    modifier: Modifier = Modifier,
    onSeeRanking: (chartId: String, title: String, difficulty: Difficulty) -> Unit = { _, _, _ -> },
    onChooseName: () -> Unit = {},
    inRoom: Boolean = false,
    onSeeRoom: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ResultsContent(state, onPlayAgain, onDone, modifier, onSeeRanking, onChooseName, inRoom, onSeeRoom)
}

@Composable
fun ResultsContent(
    state: ResultsUiState,
    onPlayAgain: (chartId: String, difficulty: Difficulty) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onSeeRanking: (chartId: String, title: String, difficulty: Difficulty) -> Unit = { _, _, _ -> },
    onChooseName: () -> Unit = {},
    inRoom: Boolean = false,
    onSeeRoom: (() -> Unit)? = null,
) {
    val back = stringResource(if (inRoom) Res.string.results_back_to_room else Res.string.results_back)
    when (state) {
        ResultsUiState.Loading -> LoadingState(modifier)

        ResultsUiState.Missing -> EmptyState(
            title = stringResource(Res.string.results_missing),
            message = "",
            actionLabel = back,
            onAction = onDone,
            modifier = modifier,
        )

        is ResultsUiState.Ready -> ReadyResults(
            state,
            onPlayAgain.takeUnless { inRoom },
            onSeeRoom.takeIf { inRoom },
            ResultsBack(back, onDone),
            modifier,
            onSeeRanking,
            onChooseName,
        )
    }
}

/** O botão de voltar do Resultado: o texto ("Voltar" ou "Voltar à sala") e a ação. */
private class ResultsBack(val label: String, val onClick: () -> Unit)

/** O Resultado pronto; sem [onPlayAgain] (a partida de sala), sem "Jogar de novo"; com [onSeeRoom], "Ver a sala". */
@Composable
private fun ReadyResults(
    state: ResultsUiState.Ready,
    onPlayAgain: ((chartId: String, difficulty: Difficulty) -> Unit)?,
    onSeeRoom: (() -> Unit)?,
    back: ResultsBack,
    modifier: Modifier,
    onSeeRanking: (chartId: String, title: String, difficulty: Difficulty) -> Unit,
    onChooseName: () -> Unit,
) {
    val record = state.record
    // O idioma da interface (no celular, o da configuração; no PC, o do sistema), como no placar do Hud.
    val locale = java.util.Locale.forLanguageTag(Locale.current.toLanguageTag())
    val score = remember(record.score, locale) { NumberFormat.getIntegerInstance(locale).format(record.score) }
    val accuracy = remember(record.accuracy, locale) { NumberFormat.getPercentInstance(locale).format(record.accuracy) }
    // Frase para o TalkBack: "1 ponto" no singular e o recorde como frase (o selo em maiúsculas seria soletrado).
    val summary = pluralStringResource(Res.plurals.results_summary, record.score.toPluralCount(), score, accuracy) +
        if (state.isNewBest) " " + stringResource(Res.string.results_new_best_spoken) else ""
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
            StatusBadge(difficultyName(record.difficulty.ordinal), BadgeTone.NEUTRAL)
            if (state.isNewBest) StatusBadge(stringResource(Res.string.results_new_best), BadgeTone.SUCCESS)
        }
        ScoreText(record.score)
        Column(
            Modifier.fillMaxWidth().background(RiffColors.Paper, MaterialTheme.shapes.small).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Stat(stringResource(Res.string.results_accuracy), accuracy)
            Stat(stringResource(Res.string.results_max_combo), record.maxCombo.toString())
            Stat(stringResource(Res.string.results_perfect), record.perfect.toString())
            record.cleanStreakMs?.let { Stat(stringResource(Res.string.results_clean_time), minutesSeconds(it)) }
            Stat(stringResource(Res.string.results_good), record.good.toString())
            Stat(stringResource(Res.string.results_miss), record.miss.toString())
        }
        if (state.rankingOn) {
            RankingCard(state.ranking, { onSeeRanking(record.chartId, state.title, record.difficulty) }, onChooseName)
        }
        if (onPlayAgain != null) {
            PosterButton(
                stringResource(Res.string.results_play_again),
                onClick = { onPlayAgain(record.chartId, record.difficulty) },
                sound = MenuSound.CONFIRM,
            )
        }
        if (onSeeRoom != null) {
            PosterButton(stringResource(Res.string.results_see_room), onClick = onSeeRoom, sound = MenuSound.CONFIRM)
        }
        TextButton(
            onClick = back.onClick,
            modifier = Modifier.focusHighlight(CircleShape),
        ) { Text(back.label, color = RiffColors.Paper) }
    }
}

/** m:ss, como numa música. */
private fun minutesSeconds(ms: Long): String {
    val seconds = ms / MS_PER_SECOND
    return "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60

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

/** Só 1 é singular: no português o "one" vale também para 0, e "0 ponto" soa errado. */
private fun Long.toPluralCount(): Int = if (this == 1L) 1 else 2
