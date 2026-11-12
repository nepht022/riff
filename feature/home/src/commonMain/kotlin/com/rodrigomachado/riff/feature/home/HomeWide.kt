package com.rodrigomachado.riff.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.RankingTable
import com.rodrigomachado.riff.core.ui.component.KeyHint
import com.rodrigomachado.riff.core.ui.component.KeyHintBar
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.SeparationIndicator
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.input.LocalGamepad
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.home.resources.Res
import com.rodrigomachado.riff.feature.home.resources.home_keys_choose
import com.rodrigomachado.riff.feature.home.resources.home_keys_move
import com.rodrigomachado.riff.feature.home.resources.home_ranking_pc_no_strum
import com.rodrigomachado.riff.feature.home.resources.home_ranking_pc_strum
import com.rodrigomachado.riff.feature.home.resources.home_ranking_phone
import com.rodrigomachado.riff.feature.home.resources.home_ranking_support
import com.rodrigomachado.riff.feature.home.resources.home_ranking_tile
import com.rodrigomachado.riff.feature.home.resources.home_report
import com.rodrigomachado.riff.feature.home.resources.home_report_short
import com.rodrigomachado.riff.feature.home.resources.home_settings
import com.rodrigomachado.riff.feature.home.resources.home_solo
import com.rodrigomachado.riff.feature.home.resources.home_solo_support
import com.rodrigomachado.riff.feature.home.resources.home_title
import org.jetbrains.compose.resources.stringResource

/**
 * A tela inicial larga (o PC): Reportar e Configurações com texto no topo, o letreiro e três quadros iguais lado a
 * lado, cada um com a linha de apoio; no PC (com o `LocalGamepad`), a barra de teclas fixa no rodapé, com os
 * símbolos do controle quando há um ligado. Na ordem do foco: Reportar, Configurações e os quadros (setas).
 * Tudo cresce com a janela ([WideHomeSizes]): os quadros ocupam quase metade da altura, como no protótipo.
 */
@Composable
internal fun WideHome(actions: HomeActions, rankingTable: RankingTable?) {
    val pad = LocalGamepad.current
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val sizes = WideHomeSizes(wideHomeScale(maxWidth, maxHeight))
            val tileHeight = (maxHeight * TILE_SHARE).coerceIn(WIDE_MIN_TILE, WIDE_MAX_TILE)
            // Rola numa janela baixa; quando cabe, os quadros ficam no meio da altura.
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                    .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                WideTop(actions, sizes)
                Column(Modifier.fillMaxWidth()) {
                    PosterTitle(
                        stringResource(Res.string.home_title),
                        Modifier.align(Alignment.CenterHorizontally),
                        style = MaterialTheme.typography.displayLarge.sized(sizes.logo),
                        redShadow = sizes.logoShadow,
                    )
                    WideTiles(tileHeight, sizes, actions, rankingTable)
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
        if (pad != null) {
            val connected by pad.connected.collectAsState()
            KeyHintBar(homeKeys(), connected, Modifier.testTag(HomeTestTags.KEY_HINTS))
        }
    }
}

/**
 * A escala da tela inicial larga: 1 é o protótipo (1280×720, já sem a barra de teclas); segue o lado mais apertado
 * da janela, entre um mínimo (a janela mínima, 800 de largura, cabe sem cortar) e um máximo (telas 4K).
 */
internal fun wideHomeScale(width: Dp, height: Dp): Float =
    minOf(height / PROTO_HEIGHT, width / PROTO_WIDTH).coerceIn(MIN_SCALE, MAX_SCALE)

/** As medidas da tela inicial larga na escala [scale] (as do protótipo vezes a escala). */
internal class WideHomeSizes(scale: Float) {
    val logo = (LOGO_SP * scale).sp
    val logoShadow = LOGO_SHADOW * scale
    val tileGap = TILE_GAP * scale
    val topIcon = TOP_ICON * scale
    val topText = (TOP_TEXT_SP * scale).sp
    val tile = TileLook(
        icon = TILE_ICON * scale,
        title = (TILE_TITLE_SP * scale).sp,
        note = (TILE_NOTE_SP * scale).sp,
        shadow = TILE_SHADOW * scale,
        gap = Spacing.sm * scale,
    )
}

@Composable
private fun WideTop(actions: HomeActions, sizes: WideHomeSizes) {
    Box(Modifier.fillMaxWidth()) {
        TextAction(
            stringResource(Res.string.home_report_short),
            stringResource(Res.string.home_report),
            actions.onReport,
            Modifier.align(Alignment.CenterStart),
            sizes,
        ) { drawEnvelope(it) }
        SeparationIndicator(Modifier.align(Alignment.Center))
        val settings = stringResource(Res.string.home_settings)
        TextAction(settings, settings, actions.onSettings, Modifier.align(Alignment.CenterEnd), sizes) {
            drawGear(it)
        }
    }
}

/**
 * Sozinho, Sala e Ranking, iguais lado a lado: no mínimo [tileHeight] de altura e, se a fonte pedir mais, todos
 * crescem juntos (nada cortado).
 */
@Composable
private fun WideTiles(tileHeight: Dp, sizes: WideHomeSizes, actions: HomeActions, rankingTable: RankingTable?) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = sizes.tileGap),
        horizontalArrangement = Arrangement.spacedBy(sizes.tileGap),
    ) {
        val tile = Modifier.weight(1f).fillMaxHeight().heightIn(min = tileHeight)
        ModeTile(
            stringResource(Res.string.home_solo),
            note = stringResource(Res.string.home_solo_support),
            icon = { drawNote(it) },
            colors = RiffColors.TourRed to RiffColors.Paper,
            onClick = actions.onSolo,
            modifier = tile.testTag(HomeTestTags.SOLO),
            look = sizes.tile,
        )
        RoomTile(actions.onRoom, wide = true, modifier = tile.testTag(HomeTestTags.ROOM), look = sizes.tile)
        ModeTile(
            stringResource(Res.string.home_ranking_tile),
            note = stringResource(rankingNote(rankingTable)),
            icon = { drawStar(it) },
            colors = RiffColors.Ink to RiffColors.Paper,
            onClick = actions.onRanking,
            modifier = tile.testTag(HomeTestTags.RANKING),
            outlined = true,
            look = sizes.tile,
        )
    }
}

/** A linha de apoio do Ranking: a tabela do modo atual (sem ela, o que o Ranking é). */
private fun rankingNote(table: RankingTable?) = when (table) {
    RankingTable.PC_STRUM -> Res.string.home_ranking_pc_strum
    RankingTable.PC_NO_STRUM -> Res.string.home_ranking_pc_no_strum
    RankingTable.PHONE -> Res.string.home_ranking_phone
    null -> Res.string.home_ranking_support
}

/**
 * Botão de texto do topo do PC (ícone e [label]); o leitor de tela lê só a [description] (a mesma do ícone do
 * celular), uma vez.
 */
@Composable
private fun TextAction(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier,
    sizes: WideHomeSizes,
    draw: DrawScope.(Color) -> Unit,
) {
    Row(
        modifier
            .heightIn(min = TOUCH)
            .focusHighlight(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = withMenuSound(MenuSound.NAVIGATE, onClick))
            .semantics { contentDescription = description }
            .padding(horizontal = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeIcon(Modifier.size(sizes.topIcon), RiffColors.PaperDim, draw)
        Text(
            label,
            style = MaterialTheme.typography.titleSmall.sized(sizes.topText),
            color = RiffColors.Paper,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** As teclas da tela inicial: as setas movem, Enter (✕ no controle) escolhe; Esc não faz nada aqui. */
@Composable
private fun homeKeys() = listOf(
    KeyHint("← → ↑ ↓", pad = null, label = stringResource(Res.string.home_keys_move)),
    KeyHint("Enter", pad = "✕", label = stringResource(Res.string.home_keys_choose)),
)

/**
 * O protótipo: tela de 1280×720 sem a barra de teclas. A largura de referência é menor que a dele porque os textos
 * dos quadros têm folga dos lados: até 1120 de largura, o conteúdo fica do tamanho do protótipo sem apertar.
 */
private val PROTO_WIDTH = 1120.dp
private val PROTO_HEIGHT = 690.dp
private const val MIN_SCALE = 0.7f
private const val MAX_SCALE = 1.8f

/** Os quadros ocupam quase metade da altura (340 de 720 no protótipo). */
private const val TILE_SHARE = 0.47f
private val WIDE_MIN_TILE = 150.dp
private val WIDE_MAX_TILE = 640.dp

// As medidas do protótipo (escala 1).
private const val LOGO_SP = 76f
private val LOGO_SHADOW = 4.dp
private val TILE_GAP = 28.dp
private val TILE_ICON = 72.dp
private const val TILE_TITLE_SP = 46f
private const val TILE_NOTE_SP = 17f
private val TILE_SHADOW = 8.dp
private val TOP_ICON = 28.dp
private const val TOP_TEXT_SP = 18f
private val TOUCH = 44.dp
