package com.rodrigomachado.riff.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.RankingTable
import com.rodrigomachado.riff.core.ui.art.ChartArtImages
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.SeparationIndicator
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.home.resources.Res
import com.rodrigomachado.riff.feature.home.resources.home_ranking
import com.rodrigomachado.riff.feature.home.resources.home_report
import com.rodrigomachado.riff.feature.home.resources.home_settings
import com.rodrigomachado.riff.feature.home.resources.home_solo
import com.rodrigomachado.riff.feature.home.resources.home_title
import org.jetbrains.compose.resources.stringResource

object HomeTestTags {
    const val SOLO = "home_solo"
    const val ROOM = "home_room"
    const val RANKING = "home_ranking"
    const val CONTINUE = "home_continue"
    const val RECENT_BEST = "home_recent_best"
    const val KEY_HINTS = "home_key_hints"
}

/**
 * A tela inicial com as partidas do [viewModel]. Voltar a ela (depois de uma partida) relê o banco: o "Continuar"
 * já é a partida nova. Os destinos e o layout estão em [HomeContent].
 */
@Composable
@Suppress("LongParameterList") // um destino por botão da tela
fun HomeScreen(
    viewModel: HomeViewModel,
    onSolo: () -> Unit,
    onRanking: () -> Unit,
    onSettings: () -> Unit,
    onReport: () -> Unit,
    onContinue: (chartId: String, difficulty: Difficulty) -> Unit,
    modifier: Modifier = Modifier,
    onRoom: (() -> Unit)? = null,
    rankingTable: RankingTable? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.refresh() }
    HomeContent(
        state, viewModel.images, onSolo, onRanking, onSettings, onReport, onContinue, modifier, onRoom, rankingTable,
    )
}

/**
 * A tela inicial; o layout escolhe pela largura. Estreita (celular, em pé ou deitado): Reportar e Configurações nos
 * cantos, o letreiro, Sozinho e Sala grandes, "Continuar" (a última partida) e "Últimos recordes" quando há partidas
 * ([state]), e o Ranking no pé. Larga (o PC): três quadros iguais (Sozinho, Sala, Ranking, com a tabela do modo
 * atual, [rankingTable]) e, no PC (com o `LocalGamepad`), a barra de teclas. Sem [onRoom] (o Firebase da Sala não
 * configurado), a Sala fica desligada, "em breve".
 */
@Composable
@Suppress("LongParameterList") // o estado, as capas e um destino por botão da tela
fun HomeContent(
    state: HomeUiState,
    images: ChartArtImages?,
    onSolo: () -> Unit,
    onRanking: () -> Unit,
    onSettings: () -> Unit,
    onReport: () -> Unit,
    onContinue: (chartId: String, difficulty: Difficulty) -> Unit,
    modifier: Modifier = Modifier,
    onRoom: (() -> Unit)? = null,
    rankingTable: RankingTable? = null,
) {
    val actions = HomeActions(onSolo, onRoom, onRanking, onSettings, onReport)
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= WIDE_MIN_WIDTH) {
            WideHome(actions, rankingTable)
        } else {
            PhoneHome(state, images, actions, onContinue)
        }
    }
}

/** Os destinos dos botões da tela inicial; [onRoom] nulo = Sala "em breve". */
internal class HomeActions(
    val onSolo: () -> Unit,
    val onRoom: (() -> Unit)?,
    val onRanking: () -> Unit,
    val onSettings: () -> Unit,
    val onReport: () -> Unit,
)

@Composable
private fun PhoneHome(
    state: HomeUiState,
    images: ChartArtImages?,
    actions: HomeActions,
    onContinue: (chartId: String, difficulty: Difficulty) -> Unit,
) {
    val bests = state.recentBests.take(HomeViewModel.RECENT_BESTS)
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        // Os quadros ficam com o que sobra do resto: num celular baixo (ou com partidas para mostrar), encolhem.
        val reserved = RESERVED_HEIGHT +
            (if (state.last != null) CONTINUE_HEIGHT else 0.dp) +
            (if (bests.isNotEmpty()) BESTS_HEADER_HEIGHT + BEST_ROW_HEIGHT * bests.size else 0.dp)
        val tileHeight = (maxHeight - reserved).coerceIn(MIN_TILE_HEIGHT, TILE_HEIGHT)
        // Rola quando nada cabe (deitado, fonte grande); quando cabe, ocupa a tela e o Ranking fica no pé.
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.fillMaxWidth()) {
                TopIcons(actions.onReport, actions.onSettings)
                PosterTitle(
                    stringResource(Res.string.home_title),
                    Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.displayLarge,
                )
                PhoneTiles(tileHeight, actions)
                state.last?.let { ContinueCard(it, images, onContinue, Modifier.padding(top = Spacing.lg)) }
                if (bests.isNotEmpty()) RecentBests(bests, Modifier.padding(top = Spacing.lg))
            }
            PosterButton(
                stringResource(Res.string.home_ranking),
                onClick = actions.onRanking,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.md, bottom = Spacing.md)
                    .testTag(HomeTestTags.RANKING),
                minWidth = RANKING_WIDTH,
                sound = MenuSound.NAVIGATE,
            )
        }
    }
}

/** Sozinho (vermelho) e Sala (papel, "com amigos") lado a lado, os maiores da tela. */
@Composable
private fun PhoneTiles(tileHeight: Dp, actions: HomeActions) {
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        val tile = Modifier.weight(1f).height(tileHeight)
        ModeTile(
            stringResource(Res.string.home_solo),
            note = null,
            icon = { drawNote(it) },
            colors = RiffColors.TourRed to RiffColors.Paper,
            onClick = actions.onSolo,
            modifier = tile.testTag(HomeTestTags.SOLO),
        )
        RoomTile(actions.onRoom, wide = false, modifier = tile.testTag(HomeTestTags.ROOM))
    }
}

@Composable
private fun TopIcons(onReport: () -> Unit, onSettings: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        CornerIcon(stringResource(Res.string.home_report), onReport, Modifier.align(Alignment.CenterStart)) {
            drawEnvelope(it)
        }
        SeparationIndicator(Modifier.align(Alignment.Center))
        CornerIcon(stringResource(Res.string.home_settings), onSettings, Modifier.align(Alignment.CenterEnd)) {
            drawGear(it)
        }
    }
}

@Composable
private fun CornerIcon(description: String, onClick: () -> Unit, modifier: Modifier, draw: DrawScope.(Color) -> Unit) {
    IconButton(
        onClick = withMenuSound(MenuSound.NAVIGATE, onClick),
        modifier = modifier.semantics { contentDescription = description }.focusHighlight(CircleShape),
    ) {
        HomeIcon(Modifier.size(CORNER_ICON), RiffColors.PaperDim, draw)
    }
}

/** A partir desta largura, o layout do PC (o celular deitado, ~780 dp, continua no do celular). */
private val WIDE_MIN_WIDTH = 800.dp
private val TILE_HEIGHT = 210.dp
private val MIN_TILE_HEIGHT = 110.dp
private val RANKING_WIDTH = 220.dp

/** O topo (ícones e letreiro), o espaço até os quadros e o Ranking no pé. */
private val RESERVED_HEIGHT = 300.dp

/** O "Continuar" com o espaço acima dele. */
private val CONTINUE_HEIGHT = 112.dp

/** O título dos "Últimos recordes" com o espaço acima; e cada linha. */
private val BESTS_HEADER_HEIGHT = 48.dp
private val BEST_ROW_HEIGHT = 40.dp
private val CORNER_ICON = 24.dp
