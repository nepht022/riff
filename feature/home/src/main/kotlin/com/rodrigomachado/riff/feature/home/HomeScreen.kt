package com.rodrigomachado.riff.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.SeparationIndicator
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

object HomeTestTags {
    const val SOLO = "home_solo"
    const val ROOM = "home_room"
    const val RANKING = "home_ranking"
}

/**
 * A tela inicial (maquete escolhida, opção 1): Reportar e Configurações nos cantos do topo, com a palheta da
 * separação no meio (só com separação ou fila); o letreiro; Sozinho e Sala lado a lado; o Ranking no pé.
 */
@Composable
fun HomeScreen(
    onSolo: () -> Unit,
    onRanking: () -> Unit,
    onSettings: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        // Os quadros ficam com o que sobra do topo, do letreiro e do Ranking: num celular baixo, encolhem.
        val tileHeight = (maxHeight - RESERVED_HEIGHT).coerceIn(MIN_TILE_HEIGHT, TILE_HEIGHT)
        HomeLayout(tileHeight, maxHeight, onSolo, onRanking, onSettings, onReport)
    }
}

@Composable
private fun HomeLayout(
    tileHeight: Dp,
    screenHeight: Dp,
    onSolo: () -> Unit,
    onRanking: () -> Unit,
    onSettings: () -> Unit,
    onReport: () -> Unit,
) {
    // Rola quando nada cabe (deitado, fonte grande); quando cabe, ocupa a tela e o Ranking fica no pé.
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = screenHeight),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.fillMaxWidth()) { HomeTop(tileHeight, onSolo, onSettings, onReport) }
        PosterButton(
            stringResource(R.string.home_ranking),
            onClick = onRanking,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.md, bottom = Spacing.md)
                .testTag(HomeTestTags.RANKING),
            minWidth = RANKING_WIDTH,
            sound = MenuSound.NAVIGATE,
        )
    }
}

@Composable
private fun ColumnScope.HomeTop(tileHeight: Dp, onSolo: () -> Unit, onSettings: () -> Unit, onReport: () -> Unit) {
    TopIcons(onReport, onSettings)
    PosterTitle(
        stringResource(R.string.home_title),
        Modifier.align(Alignment.CenterHorizontally),
        style = MaterialTheme.typography.displayLarge,
    )
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        val tile = Modifier.weight(1f).height(tileHeight)
        ModeTile(
            stringResource(R.string.home_solo),
            note = null,
            icon = { drawNote(it) },
            colors = RiffColors.TourRed to RiffColors.Paper,
            onClick = onSolo,
            modifier = tile.testTag(HomeTestTags.SOLO),
        )
        ModeTile(
            stringResource(R.string.home_room),
            note = stringResource(R.string.home_coming_soon),
            icon = { drawFlag(it) },
            colors = RoomBackground to RiffColors.PaperDim,
            onClick = null,
            modifier = tile.testTag(HomeTestTags.ROOM),
            description = stringResource(R.string.home_room_coming_soon),
        )
    }
}

@Composable
private fun TopIcons(onReport: () -> Unit, onSettings: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        CornerIcon(stringResource(R.string.home_report), onReport, Modifier.align(Alignment.CenterStart)) {
            drawEnvelope(it)
        }
        SeparationIndicator(Modifier.align(Alignment.Center))
        CornerIcon(stringResource(R.string.home_settings), onSettings, Modifier.align(Alignment.CenterEnd)) {
            drawGear(it)
        }
    }
}

@Composable
private fun CornerIcon(description: String, onClick: () -> Unit, modifier: Modifier, draw: DrawScope.(Color) -> Unit) {
    IconButton(
        onClick = withMenuSound(MenuSound.NAVIGATE, onClick),
        modifier = modifier.semantics { contentDescription = description },
    ) {
        HomeIcon(Modifier.size(CORNER_ICON), RiffColors.PaperDim, draw)
    }
}

/** Quadro alto da tela inicial; sem [onClick], desligado ("em breve") e lido por [description]. */
@Composable
@Suppress("LongParameterList") // texto, ícone, cores e o estado desligado de cada quadro
private fun ModeTile(
    title: String,
    note: String?,
    icon: DrawScope.(Color) -> Unit,
    colors: Pair<Color, Color>,
    onClick: (() -> Unit)?,
    modifier: Modifier,
    description: String = title,
) {
    val (background, content) = colors
    val action = if (onClick == null) {
        Modifier.clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
            disabled()
        }
    } else {
        Modifier.clickable(role = Role.Button, onClick = withMenuSound(MenuSound.NAVIGATE, onClick))
    }
    Column(
        modifier
            // Sombra dura deslocada, como os cartazes do app.
            .drawBehind { drawRect(Color.Black, Offset(SHADOW.toPx(), SHADOW.toPx()), size) }
            .background(background, MaterialTheme.shapes.small)
            .then(action),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HomeIcon(Modifier.size(TILE_ICON), content, icon)
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = content,
            textAlign = TextAlign.Center,
        )
        note?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = content) }
    }
}

private val RoomBackground = Color(0xFF3A2E28)
private val TILE_HEIGHT = 210.dp
private val MIN_TILE_HEIGHT = 110.dp
private val RANKING_WIDTH = 220.dp

/** O topo (ícones e letreiro), o espaço até os quadros e o Ranking no pé. */
private val RESERVED_HEIGHT = 300.dp
private val TILE_ICON = 40.dp
private val CORNER_ICON = 24.dp
private val SHADOW = 4.dp
