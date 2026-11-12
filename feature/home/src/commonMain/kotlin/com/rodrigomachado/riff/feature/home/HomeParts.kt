package com.rodrigomachado.riff.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.art.ChartArtImages
import com.rodrigomachado.riff.core.ui.component.ChartThumb
import com.rodrigomachado.riff.core.ui.component.difficultyName
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.home.resources.Res
import com.rodrigomachado.riff.feature.home.resources.home_coming_soon
import com.rodrigomachado.riff.feature.home.resources.home_continue
import com.rodrigomachado.riff.feature.home.resources.home_recent_bests
import com.rodrigomachado.riff.feature.home.resources.home_record
import com.rodrigomachado.riff.feature.home.resources.home_room
import com.rodrigomachado.riff.feature.home.resources.home_room_coming_soon
import com.rodrigomachado.riff.feature.home.resources.home_room_friends
import com.rodrigomachado.riff.feature.home.resources.home_room_support
import org.jetbrains.compose.resources.stringResource
import java.text.NumberFormat

/**
 * A Sala: papel com tinta quando ligada ("com amigos" no celular; [wide], a linha de apoio do PC); apagada e
 * "em breve" sem o Firebase configurado.
 */
@Composable
internal fun RoomTile(onRoom: (() -> Unit)?, wide: Boolean, modifier: Modifier, look: TileLook = TileLook()) {
    if (onRoom != null) {
        ModeTile(
            stringResource(Res.string.home_room),
            note = stringResource(if (wide) Res.string.home_room_support else Res.string.home_room_friends),
            icon = { drawFlag(it) },
            colors = RiffColors.Paper to RiffColors.Ink,
            onClick = onRoom,
            modifier = modifier,
            look = look,
        )
    } else {
        ModeTile(
            stringResource(Res.string.home_room),
            note = stringResource(Res.string.home_coming_soon),
            icon = { drawFlag(it) },
            colors = RoomBackground to RiffColors.PaperDim,
            onClick = null,
            modifier = modifier,
            description = stringResource(Res.string.home_room_coming_soon),
            look = look,
        )
    }
}

/**
 * As medidas de dentro de um quadro: o ícone, o título e a linha de apoio (sem tamanho, os do tema) e a sombra dura.
 * O padrão é o do celular; o PC passa as suas, que crescem com a janela.
 */
internal data class TileLook(
    val icon: Dp = TILE_ICON,
    val title: TextUnit = TextUnit.Unspecified,
    val note: TextUnit = TextUnit.Unspecified,
    val shadow: Dp = SHADOW,
    val gap: Dp = 0.dp,
)

/**
 * Quadro alto da tela inicial, com a linha de apoio [note] embaixo do título; sem [onClick], desligado ("em breve")
 * e lido por [description]. [outlined]: o contorno de papel, para o quadro escuro não sumir no fundo; [look]: as
 * medidas de dentro.
 */
@Composable
@Suppress("LongParameterList") // texto, ícone, cores e o estado desligado de cada quadro
internal fun ModeTile(
    title: String,
    note: String?,
    icon: DrawScope.(Color) -> Unit,
    colors: Pair<Color, Color>,
    onClick: (() -> Unit)?,
    modifier: Modifier,
    description: String = title,
    outlined: Boolean = false,
    look: TileLook = TileLook(),
) {
    val (background, content) = colors
    val action = if (onClick == null) {
        Modifier.clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
            disabled()
        }
    } else {
        Modifier
            .focusHighlight(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = withMenuSound(MenuSound.NAVIGATE, onClick))
    }
    val outline = if (outlined) Modifier.border(OUTLINE, RiffColors.PaperDim, MaterialTheme.shapes.small) else Modifier
    Column(
        modifier
            // Sombra dura deslocada, como os cartazes do app.
            .drawBehind { drawRect(Color.Black, Offset(look.shadow.toPx(), look.shadow.toPx()), size) }
            .background(background, MaterialTheme.shapes.small)
            .then(outline)
            .then(action)
            .padding(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HomeIcon(Modifier.padding(bottom = look.gap).size(look.icon), content, icon)
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.sized(look.title),
            color = content,
            textAlign = TextAlign.Center,
        )
        note?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium.sized(look.note),
                color = content,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** O estilo com o tamanho [size] (e a altura da linha proporcional); sem tamanho, o próprio estilo. */
internal fun TextStyle.sized(size: TextUnit): TextStyle =
    if (size.isUnspecified) this else copy(fontSize = size, lineHeight = size * LINE_HEIGHT)

/**
 * "Continuar": a última partida (capa, título, artista · dificuldade e o recorde); tocar abre a partida dessa música
 * nessa dificuldade. O leitor de tela lê o cartão de uma vez, como um botão.
 */
@Composable
internal fun ContinueCard(
    match: HomeMatch,
    images: ChartArtImages?,
    onContinue: (chartId: String, difficulty: Difficulty) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind { drawRect(Color.Black, Offset(SHADOW.toPx(), SHADOW.toPx()), size) }
            .background(RiffColors.Ink, MaterialTheme.shapes.small)
            .border(OUTLINE, RiffColors.PaperDim, MaterialTheme.shapes.small)
            .focusHighlight(MaterialTheme.shapes.small)
            .clickable(
                role = Role.Button,
                onClick = withMenuSound(MenuSound.NAVIGATE) { onContinue(match.chartId, match.difficulty) },
            )
            .testTag(HomeTestTags.CONTINUE)
            .padding(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChartThumb(match.chartId, match.title, CONTINUE_THUMB, images)
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.home_continue),
                style = MaterialTheme.typography.labelMedium,
                color = RiffColors.PaperDim,
            )
            Text(
                match.title,
                style = MaterialTheme.typography.titleMedium,
                color = RiffColors.Paper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ArtistAndDifficulty(match)
            Text(
                stringResource(Res.string.home_record, formatScore(match.best)),
                style = MaterialTheme.typography.labelLarge,
                color = RiffColors.Paper,
            )
        }
        Box(
            Modifier.size(PLAY_CIRCLE).background(RiffColors.TourRed, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            HomeIcon(Modifier.size(PLAY_ICON), RiffColors.Paper) { drawPlay(it) }
        }
    }
}

/** "Artista · Dificuldade": dois textos (o artista pode ser cortado; a dificuldade, nunca), como no Resultado. */
@Composable
private fun ArtistAndDifficulty(match: HomeMatch) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (match.artist.isNotBlank()) {
            Text(
                match.artist,
                Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodySmall,
                color = RiffColors.PaperDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(" · ", style = MaterialTheme.typography.bodySmall, color = RiffColors.PaperDim)
        }
        Text(
            difficultyName(match.difficulty.ordinal),
            style = MaterialTheme.typography.bodySmall,
            color = RiffColors.PaperDim,
            maxLines = 1,
        )
    }
}

/** "Últimos recordes": título e pontos de cada um (até 3), uma linha lida de uma vez cada. */
@Composable
internal fun RecentBests(bests: List<HomeMatch>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(Res.string.home_recent_bests),
            style = MaterialTheme.typography.titleSmall,
            color = RiffColors.PaperDim,
            modifier = Modifier.semantics { heading() }.padding(bottom = Spacing.xs),
        )
        bests.forEach { best ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = BEST_ROW)
                    .semantics(mergeDescendants = true) {}
                    .testTag(HomeTestTags.RECENT_BEST),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    best.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = RiffColors.Paper,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(formatScore(best.best), style = MaterialTheme.typography.titleMedium, color = RiffColors.Paper)
            }
        }
    }
}

/** Os pontos no idioma da interface (no celular, o da configuração; no PC, o do sistema), como no Resultado. */
@Composable
private fun formatScore(score: Long): String {
    val locale = java.util.Locale.forLanguageTag(Locale.current.toLanguageTag())
    return remember(score, locale) { NumberFormat.getIntegerInstance(locale).format(score) }
}

private val RoomBackground = Color(0xFF3A2E28)
private const val LINE_HEIGHT = 1.2f
private val TILE_ICON = 40.dp
private val SHADOW = 4.dp
private val OUTLINE = 1.dp
private val CONTINUE_THUMB = 64.dp
private val PLAY_CIRCLE = 44.dp
private val PLAY_ICON = 18.dp
private val BEST_ROW = 40.dp
