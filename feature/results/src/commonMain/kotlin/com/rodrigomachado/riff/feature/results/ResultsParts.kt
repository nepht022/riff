package com.rodrigomachado.riff.feature.results

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.ScoreRecord
import com.rodrigomachado.riff.core.ui.art.ChartArtImages
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.ChartThumb
import com.rodrigomachado.riff.core.ui.component.KeyHint
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.ResultGrade
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.difficultyName
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.core.ui.theme.scoreTextStyle
import com.rodrigomachado.riff.feature.results.resources.Res
import com.rodrigomachado.riff.feature.results.resources.results_accuracy
import com.rodrigomachado.riff.feature.results.resources.results_clean_time
import com.rodrigomachado.riff.feature.results.resources.results_good
import com.rodrigomachado.riff.feature.results.resources.results_grade
import com.rodrigomachado.riff.feature.results.resources.results_grade_spoken
import com.rodrigomachado.riff.feature.results.resources.results_keys_back
import com.rodrigomachado.riff.feature.results.resources.results_keys_choose
import com.rodrigomachado.riff.feature.results.resources.results_keys_move
import com.rodrigomachado.riff.feature.results.resources.results_max_combo
import com.rodrigomachado.riff.feature.results.resources.results_miss
import com.rodrigomachado.riff.feature.results.resources.results_new_best
import com.rodrigomachado.riff.feature.results.resources.results_perfect
import com.rodrigomachado.riff.feature.results.resources.results_previous_best
import org.jetbrains.compose.resources.stringResource

/** A capa do mapa ao lado do título (letreiro) e de "artista · dificuldade". */
@Composable
internal fun ResultsHeader(state: ResultsUiState.Ready, images: ChartArtImages?, cover: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        ChartThumb(state.record.chartId, state.title, cover, images)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            PosterTitle(state.title, style = MaterialTheme.typography.headlineSmall)
            // Dois textos (o artista pode ser cortado; a dificuldade, nunca).
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.artist.isNotBlank()) {
                    Text(
                        state.artist,
                        Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        color = RiffColors.PaperDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(" · ", style = MaterialTheme.typography.titleMedium, color = RiffColors.PaperDim)
                }
                Text(
                    difficultyName(state.record.difficulty.ordinal),
                    style = MaterialTheme.typography.titleMedium,
                    color = RiffColors.Paper,
                )
            }
        }
    }
}

/**
 * Os pontos grandes (em [scoreSize]) e, no recorde novo, o selo e o recorde de antes ("antes: N", quando havia); ao
 * lado, o carimbo da nota. Com a fonte grande, o carimbo desce para baixo dos pontos.
 */
@Composable
internal fun ScoreAndGrade(state: ResultsUiState.Ready, texts: ResultsTexts, scoreSize: TextUnit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(
            Modifier.align(Alignment.CenterVertically).padding(end = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            PosterTitle(texts.score, style = scoreTextStyle().copy(fontSize = scoreSize), heading = false)
            if (state.isNewBest) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    StatusBadge(
                        stringResource(Res.string.results_new_best),
                        BadgeTone.SUCCESS,
                        Modifier.align(Alignment.CenterVertically),
                    )
                    texts.previousBest?.let {
                        Text(
                            stringResource(Res.string.results_previous_best, it),
                            Modifier.align(Alignment.CenterVertically),
                            style = MaterialTheme.typography.titleMedium,
                            color = RiffColors.PaperDim,
                        )
                    }
                }
            }
        }
        GradeStamp(ResultGrade.of(state.record.accuracy), Modifier.align(Alignment.CenterVertically))
    }
}

/**
 * O carimbo da nota (S, A, B ou C), inclinado como um carimbo batido no pôster: vermelho com moldura dupla de papel e
 * sombra dura. Lido de uma vez ("Nota B").
 */
@Composable
internal fun GradeStamp(grade: ResultGrade, modifier: Modifier = Modifier) {
    val spoken = stringResource(Res.string.results_grade_spoken, grade.name)
    Box(
        modifier
            .testTag(ResultsTestTags.GRADE)
            .clearAndSetSemantics { contentDescription = spoken }
            // Folga para a inclinação e a sombra não encostarem no vizinho.
            .padding(Spacing.sm)
            .rotate(STAMP_TILT_DEGREES)
            .drawBehind { drawRect(Color.Black, Offset(STAMP_SHADOW.toPx(), STAMP_SHADOW.toPx()), size) }
            .background(RiffColors.TourRed)
            .padding(STAMP_INSET)
            .border(STAMP_LINE, RiffColors.Paper)
            .padding(STAMP_INSET)
            .border(STAMP_THIN_LINE, RiffColors.Paper)
            .defaultMinSize(minWidth = STAMP_MIN)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(Res.string.results_grade),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                color = RiffColors.Paper,
            )
            Text(grade.name, style = MaterialTheme.typography.displayMedium, color = RiffColors.Paper)
        }
    }
}

/** A precisão em número e em barra (vermelha sobre tinta) e, quando medido, o tempo sem errar. */
@Composable
internal fun AccuracyBar(record: ScoreRecord, accuracy: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.results_accuracy),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = RiffColors.Paper,
            )
            Text(accuracy, style = MaterialTheme.typography.titleLarge, color = RiffColors.Paper)
        }
        // A barra é desenho: o número ao lado já diz a precisão.
        Box(
            Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .clearAndSetSemantics {}
                .background(RiffColors.Ink)
                .border(BAR_LINE, RiffColors.PaperDim),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(record.accuracy.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(RiffColors.TourRed),
            )
        }
        // Partida de antes da medida: sem a linha (nada de "0:00" falso).
        record.cleanStreakMs?.let {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.results_clean_time),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = RiffColors.PaperDim,
                )
                Text(minutesSeconds(it), style = MaterialTheme.typography.titleMedium, color = RiffColors.Paper)
            }
        }
    }
}

/** Os quatro blocos de papel (maior combo, perfeitos, bons, erros), [columns] por linha, da mesma altura. */
@Composable
internal fun StatBlocks(record: ScoreRecord, columns: Int) {
    val stats = listOf(
        stringResource(Res.string.results_max_combo) to record.maxCombo,
        stringResource(Res.string.results_perfect) to record.perfect,
        stringResource(Res.string.results_good) to record.good,
        stringResource(Res.string.results_miss) to record.miss,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        stats.chunked(columns).forEach { line ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                line.forEach { (label, value) ->
                    StatBlock(label, value.toString(), Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/** Um bloco: o número grande e o nome embaixo, tinta sobre papel, sombra dura. Lido de uma vez ("Bons, 10"). */
@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .padding(end = BLOCK_SHADOW, bottom = BLOCK_SHADOW)
            .drawBehind { drawRect(Color.Black, Offset(BLOCK_SHADOW.toPx(), BLOCK_SHADOW.toPx()), size) }
            .background(RiffColors.Paper)
            .clearAndSetSemantics { contentDescription = "$label, $value" }
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = RiffColors.Ink)
        Text(label, style = MaterialTheme.typography.labelMedium, color = RiffColors.Ink)
    }
}

/**
 * Embaixo, lado a lado: o de voltar (papel) à esquerda e o principal (vermelho, "Jogar de novo" ou "Ver a sala") à
 * direita, da mesma largura.
 */
@Composable
internal fun ResultsButtons(actions: ResultsActions) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val half = (maxWidth - Spacing.md) / 2
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sem som próprio: o de voltar toca na navegação (a tela sai da pilha), venha a volta de onde vier.
            PaperButton(actions.back.label, actions.back.onClick, Modifier.weight(1f).fillMaxHeight())
            actions.main?.let {
                PosterButton(
                    it.label,
                    onClick = it.onClick,
                    modifier = Modifier.weight(1f),
                    sound = MenuSound.CONFIRM,
                    minWidth = half - BUTTON_SHADOW,
                )
            }
        }
    }
}

/** O botão secundário de pôster: papel com texto em tinta e sombra dura, sem inclinação. */
@Composable
private fun PaperButton(text: String, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .padding(end = BUTTON_SHADOW, bottom = BUTTON_SHADOW)
            .drawBehind { drawRect(RiffColors.Ink, Offset(BUTTON_SHADOW.toPx(), BUTTON_SHADOW.toPx()), size) }
            .background(RiffColors.Paper)
            .focusHighlight(RectangleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = BUTTON_MIN_HEIGHT)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = RiffColors.Ink, textAlign = TextAlign.Center)
    }
}

/** As teclas do Resultado no PC. */
@Composable
internal fun resultsKeys() = listOf(
    KeyHint("← → ↑ ↓", pad = null, label = stringResource(Res.string.results_keys_move)),
    KeyHint("Enter", pad = "✕", label = stringResource(Res.string.results_keys_choose)),
    KeyHint("Esc", pad = "○", label = stringResource(Res.string.results_keys_back)),
)

/** m:ss, como numa música. */
internal fun minutesSeconds(ms: Long): String {
    val seconds = ms / MS_PER_SECOND
    return "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60
private const val STAMP_TILT_DEGREES = -10f
private val STAMP_SHADOW = 5.dp
private val STAMP_INSET = 4.dp
private val STAMP_LINE = 2.dp
private val STAMP_THIN_LINE = 1.dp
private val STAMP_MIN = 72.dp
private val BAR_HEIGHT = 14.dp
private val BAR_LINE = 1.dp
private val BLOCK_SHADOW = 4.dp
private val BUTTON_SHADOW = 5.dp
private val BUTTON_MIN_HEIGHT = 48.dp
