package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.gameplay.resources.Res
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_live_board
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_live_left
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_live_stale
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_live_you
import org.jetbrains.compose.resources.stringResource
import java.text.NumberFormat

/** A altura de cada linha do placar ao vivo (a troca de lugar desliza essa distância por posição). */
internal val LIVE_ROW_HEIGHT = 22.dp

/** Quanto dura o deslizar das linhas quando as posições trocam. */
internal const val LIVE_SWAP_MS = 400L

/** O fundo do bloco: escuro, para ler por cima da imagem do mapa sem esconder o que está atrás. */
private const val LIVE_SCRIM_ALPHA = 0.72f
private val LIVE_POSITION_WIDTH = 14.dp

/**
 * O placar ao vivo minimizado da partida de sala, com o meu ao vivo (o do [hud], a cada acerto) e o dos outros
 * ([others], a cada leitura da sala). Lê o estado só aqui: o resto da partida não recompõe por causa dele.
 */
@Composable
internal fun LiveBoardBlock(
    others: State<List<LiveScore>>,
    hud: HudState,
    reducedMotion: Boolean,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val mine = LiveScore("", "", hud.score, hud.combo, hud.maxCombo, accuracy = 0f, finished = false)
    LiveBoardHud(liveStandings(others.value, mine), reducedMotion, width, modifier)
}

/**
 * Até 4 linhas "posição · nome · pontos", a minha destacada (o vermelho do carimbo, e `selected` para o leitor de
 * tela); "saiu" e "sem conexão" no lugar dos pontos, apagados. Cada linha fica no lugar da sua posição e desliza até o
 * novo quando a ordem muda (sem animação com [reducedMotion]).
 */
@Composable
internal fun LiveBoardHud(lines: List<LiveLine>, reducedMotion: Boolean, width: Dp, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.gameplay_live_board)
    Box(
        modifier
            .width(width)
            .height(LIVE_ROW_HEIGHT * lines.size)
            .background(RiffColors.StageBlack.copy(alpha = LIVE_SCRIM_ALPHA))
            .semantics { contentDescription = description }
            .testTag(GameplayTestTags.LIVE_BOARD),
    ) {
        lines.forEachIndexed { index, line ->
            key(line.key) {
                val top by animateDpAsState(
                    LIVE_ROW_HEIGHT * index,
                    if (reducedMotion) snap() else tween(LIVE_SWAP_MS.toInt()),
                    label = "liveLine",
                )
                LiveRow(line, Modifier.offset { IntOffset(0, top.roundToPx()) })
            }
        }
    }
}

@Composable
private fun LiveRow(line: LiveLine, modifier: Modifier) {
    val score = line.score
    val dim = score.left || score.stale
    val color = if (dim) RiffColors.PaperDim else RiffColors.Paper
    val style = MaterialTheme.typography.titleSmall
    Row(
        modifier
            .fillMaxWidth()
            .height(LIVE_ROW_HEIGHT)
            .background(if (line.mine) RiffColors.TourRed else Color.Transparent)
            .padding(horizontal = Spacing.sm)
            .semantics(mergeDescendants = true) { selected = line.mine }
            .testTag(GameplayTestTags.liveLine(line.key)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(line.position.toString(), Modifier.width(LIVE_POSITION_WIDTH), color, style = style)
        Text(
            if (line.mine) stringResource(Res.string.gameplay_live_you) else score.name,
            Modifier.weight(1f).padding(end = Spacing.xs),
            color,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            when {
                score.left -> stringResource(Res.string.gameplay_live_left)
                score.stale -> stringResource(Res.string.gameplay_live_stale)
                else -> formatPoints(score.score)
            },
            color = color,
            style = if (dim) MaterialTheme.typography.bodySmall else style,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

/** Os pontos com o separador de milhar do idioma (como o placar grande). */
@Composable
private fun formatPoints(points: Long): String {
    val locale = Locale.current
    return remember(points, locale) {
        NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag(locale.toLanguageTag())).format(points)
    }
}
