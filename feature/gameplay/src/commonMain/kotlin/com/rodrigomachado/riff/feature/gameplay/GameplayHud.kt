package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.stage.JudgmentText
import com.rodrigomachado.riff.core.ui.stage.MultiplierStamp
import com.rodrigomachado.riff.core.ui.stage.ScoreText
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.gameplay.resources.Res
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_combo
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_judgment_good
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_judgment_perfect
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_no_separate_guitar
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_pause
import org.jetbrains.compose.resources.stringResource

private const val COMBO_MIN_TO_SHOW = 10

/** O placar em pé (celular): tudo por cima da pista, cada um no seu canto. */
@Composable
internal fun Hud(
    session: GameSession,
    hud: HudState,
    frame: () -> Long,
    reducedMotion: Boolean,
    onPause: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.padding(Spacing.md)) {
        // Cada um no seu canto: o placar crescendo não empurra o Pausar, que fica sempre no centro.
        ScoreText(hud.score, Modifier.align(Alignment.TopStart))
        PauseButton(onPause, Modifier.align(Alignment.TopCenter))
        MultiplierStamp(hud.multiplier, Modifier.align(Alignment.TopEnd))
        ComboText(hud, Modifier.align(Alignment.TopCenter).padding(top = Spacing.xl * 2))
        JudgmentPop(session, hud, frame, reducedMotion, Modifier.align(Alignment.Center))
        if (hud.showingNotice) {
            NoSeparateGuitarNotice(Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xl * 3))
        }
    }
}

/**
 * O placar deitado (PC), nas laterais da pista central de largura [highwayWidth], sem cobri-la: placar e combo à
 * esquerda (junto da pista), multiplicador e Pausar à direita. O julgamento fica na pista ([JudgmentPop]).
 */
@Composable
internal fun SideHud(hud: HudState, onPause: () -> Unit, highwayWidth: Dp, modifier: Modifier) {
    Row(modifier) {
        Box(Modifier.weight(1f).fillMaxHeight().padding(Spacing.md)) {
            Column(
                Modifier.align(Alignment.TopEnd),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                horizontalAlignment = Alignment.End,
            ) {
                ScoreText(hud.score)
                ComboText(hud, Modifier)
            }
            if (hud.showingNotice) {
                NoSeparateGuitarNotice(Modifier.align(Alignment.BottomEnd), TextAlign.End)
            }
        }
        Spacer(Modifier.width(highwayWidth))
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            MultiplierStamp(hud.multiplier)
            PauseButton(onPause, Modifier)
        }
    }
}

@Composable
private fun PauseButton(onPause: () -> Unit, modifier: Modifier) {
    TextButton(onClick = onPause, modifier = modifier.testTag(GameplayTestTags.PAUSE).focusHighlight(CircleShape)) {
        Text(stringResource(Res.string.gameplay_pause), color = RiffColors.Paper)
    }
}

@Composable
private fun ComboText(hud: HudState, modifier: Modifier) {
    if (hud.combo < COMBO_MIN_TO_SHOW) return
    Text(
        stringResource(Res.string.gameplay_combo, hud.combo),
        style = MaterialTheme.typography.labelLarge,
        color = RiffColors.Paper,
        modifier = modifier,
    )
}

/** "PERFEITO!"/"BOM!" logo depois do acerto, com o salto relido a cada quadro só nesta camada. */
@Composable
internal fun JudgmentPop(
    session: GameSession,
    hud: HudState,
    frame: () -> Long,
    reducedMotion: Boolean,
    modifier: Modifier,
) {
    val judgment = hud.judgment
    if (judgment == null || !hud.showingJudgment) return
    val label = if (judgment == Judgment.PERFECT) {
        Res.string.gameplay_judgment_perfect
    } else {
        Res.string.gameplay_judgment_good
    }
    JudgmentText(
        stringResource(label),
        modifier.graphicsLayer {
            frame() // relê a cada quadro só esta camada, sem recompor o placar
            val bounce = judgmentBounce(session.judgmentAgeMs(), reducedMotion)
            scaleX = bounce
            scaleY = bounce
        },
    )
}

/** As faixas separadas falharam: a partida segue com um arquivo só (spec da guitarra separada §4). */
@Composable
private fun NoSeparateGuitarNotice(modifier: Modifier, textAlign: TextAlign = TextAlign.Unspecified) {
    Text(
        stringResource(Res.string.gameplay_no_separate_guitar),
        style = MaterialTheme.typography.bodyMedium,
        color = RiffColors.PaperDim,
        textAlign = textAlign,
        modifier = modifier,
    )
}
