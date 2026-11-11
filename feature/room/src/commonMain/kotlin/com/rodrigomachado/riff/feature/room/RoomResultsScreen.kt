package com.rodrigomachado.riff.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.room.BoardLine
import com.rodrigomachado.riff.core.room.RoomDevice
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.WithPcKeyHints
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.core.ui.theme.oswald
import com.rodrigomachado.riff.feature.room.resources.Res
import com.rodrigomachado.riff.feature.room.resources.room_pc
import com.rodrigomachado.riff.feature.room.resources.room_phone
import com.rodrigomachado.riff.feature.room.resources.room_results_accuracy
import com.rodrigomachado.riff.feature.room.resources.room_results_back
import com.rodrigomachado.riff.feature.room.resources.room_results_empty
import com.rodrigomachado.riff.feature.room.resources.room_results_left
import com.rodrigomachado.riff.feature.room.resources.room_results_max_combo
import com.rodrigomachado.riff.feature.room.resources.room_results_playing
import com.rodrigomachado.riff.feature.room.resources.room_results_round
import com.rodrigomachado.riff.feature.room.resources.room_results_stale
import com.rodrigomachado.riff.feature.room.resources.room_results_title
import com.rodrigomachado.riff.feature.room.resources.room_results_waiting
import com.rodrigomachado.riff.feature.room.resources.room_you
import org.jetbrains.compose.resources.stringResource
import java.text.NumberFormat

/** O Resultado da sala ao vivo; "Voltar à sala" ([onBackToRoom]) volta à tela da sala. */
@Composable
fun RoomResultsScreen(viewModel: RoomResultsViewModel, onBackToRoom: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RoomResultsContent(state, onBackToRoom, modifier)
}

/**
 * O pódio (os 3 primeiros que não saíram: o 1.º no meio, mais alto) e a classificação de todos, com a posição, o
 * nome, o aparelho, os pontos, a precisão e o maior combo; "saiu", "sem conexão" e "jogando" carimbados; a minha
 * linha com a borda vermelha. "Voltar à sala" está sempre lá, também antes de a sala chegar.
 */
@Composable
fun RoomResultsContent(state: RoomResultsUiState, onBackToRoom: () -> Unit, modifier: Modifier = Modifier) {
    WithPcKeyHints(roomMenuKeys(), modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            PosterTitle(stringResource(Res.string.room_results_title), style = MaterialTheme.typography.headlineMedium)
            when (state) {
                RoomResultsUiState.Loading -> CircularProgressIndicator(
                    color = RiffColors.Paper,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                is RoomResultsUiState.Ready -> Standings(state)
            }
            PosterButton(
                stringResource(Res.string.room_results_back),
                onClick = onBackToRoom,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun Standings(state: RoomResultsUiState.Ready) {
    Text(
        stringResource(Res.string.room_results_round, state.round),
        style = MaterialTheme.typography.titleMedium,
        color = RiffColors.PaperDim,
    )
    if (state.lines.isEmpty()) {
        Text(
            stringResource(Res.string.room_results_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = RiffColors.Paper,
        )
        return
    }
    if (state.waiting) {
        Text(
            stringResource(Res.string.room_results_waiting),
            style = MaterialTheme.typography.bodyLarge,
            color = RiffColors.Paper,
        )
    }
    Podium(state.lines.filterNot { it.left }.take(PODIUM_PLACES))
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        state.lines.forEachIndexed { index, line -> ResultCard(index + 1, line, me = line.uid == state.me) }
    }
}

/** O pódio: o 2.º à esquerda, o 1.º no meio (vermelho, o mais alto) e o 3.º à direita. Só enfeite para o leitor. */
@Composable
private fun Podium(top: List<BoardLine>) {
    if (top.isEmpty()) return
    val places = listOfNotNull(top.getOrNull(1)?.let { 2 to it }, 1 to top[0], top.getOrNull(2)?.let { 3 to it })
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        places.forEach { (place, line) -> PodiumStep(place, line, Modifier.weight(1f, fill = false)) }
    }
}

@Composable
private fun PodiumStep(place: Int, line: BoardLine, modifier: Modifier) {
    val first = place == 1
    Column(modifier.widthIn(max = PODIUM_MAX_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            line.name,
            style = MaterialTheme.typography.titleMedium,
            color = RiffColors.Paper,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(points(line.score), style = MaterialTheme.typography.bodyMedium, color = RiffColors.PaperDim)
        Box(
            Modifier.fillMaxWidth().padding(top = Spacing.xs, end = HardShadow, bottom = HardShadow)
                .height(stepHeight(place))
                .drawBehind { drawRect(Color.Black, Offset(HardShadow.toPx(), HardShadow.toPx()), size) }
                .background(if (first) RiffColors.TourRed else RiffColors.Paper),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                place.toString(),
                style = TextStyle(fontFamily = oswald(), fontWeight = FontWeight.SemiBold, fontSize = PLACE_SIZE),
                color = if (first) RiffColors.Paper else RiffColors.Ink,
            )
        }
    }
}

/**
 * Uma linha da classificação, num cartão de papel lido de uma vez pelo leitor de tela. Quem saiu fica no papel
 * apagado, com o texto esmaecido (o carimbo continua legível).
 */
@Composable
private fun ResultCard(position: Int, line: BoardLine, me: Boolean) {
    val shape = MaterialTheme.shapes.small
    Row(
        Modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .background(if (line.left) RiffColors.PaperDim else RiffColors.Paper, shape)
            .border(ME_BORDER, if (me) RiffColors.TourRed else Color.Transparent, shape)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        val dim = Modifier.alpha(if (line.left) LEFT_ALPHA else 1f)
        Text(
            "$position",
            style = TextStyle(fontFamily = oswald(), fontWeight = FontWeight.SemiBold, fontSize = POSITION_SIZE),
            color = if (position == 1 && !line.left) RiffColors.TourRed else RiffColors.Ink,
            modifier = dim,
        )
        Column(Modifier.weight(1f).then(dim)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    line.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = RiffColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (me) {
                    Text(
                        stringResource(Res.string.room_you),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RiffColors.TourRed,
                    )
                }
            }
            val device = stringResource(if (line.device == RoomDevice.PC) Res.string.room_pc else Res.string.room_phone)
            Text(device, style = MaterialTheme.typography.bodyMedium, color = SECONDARY)
            Text(
                stringResource(Res.string.room_results_accuracy, percent(line.accuracy)) + " · " +
                    stringResource(Res.string.room_results_max_combo, line.maxCombo),
                style = MaterialTheme.typography.bodyMedium,
                color = SECONDARY,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                points(line.score),
                style = TextStyle(fontFamily = oswald(), fontWeight = FontWeight.SemiBold, fontSize = SCORE_SIZE),
                color = RiffColors.Ink,
                modifier = dim,
            )
            LineStamp(line)
        }
    }
}

/** "saiu", "sem conexão" (não mandou o final) ou "jogando" (o final ainda vai chegar); quem terminou, nada. */
@Composable
private fun LineStamp(line: BoardLine) {
    when {
        line.left -> Stamp(stringResource(Res.string.room_results_left), RiffColors.StampWarning)
        line.stale -> Stamp(stringResource(Res.string.room_results_stale), RiffColors.StampError)
        !line.finished -> Stamp(stringResource(Res.string.room_results_playing), RiffColors.Ink)
    }
}

/** Os pontos no idioma da interface (no celular, o da configuração; no PC, o do sistema), como no Resultado. */
@Composable
private fun points(score: Long): String {
    val locale = javaLocale()
    return remember(score, locale) { NumberFormat.getIntegerInstance(locale).format(score) }
}

@Composable
private fun percent(accuracy: Float): String {
    val locale = javaLocale()
    return remember(accuracy, locale) { NumberFormat.getPercentInstance(locale).format(accuracy) }
}

@Composable
private fun javaLocale(): java.util.Locale = java.util.Locale.forLanguageTag(Locale.current.toLanguageTag())

private fun stepHeight(place: Int): Dp = when (place) {
    1 -> FIRST_STEP
    2 -> SECOND_STEP
    else -> THIRD_STEP
}

private const val PODIUM_PLACES = 3
private const val LEFT_ALPHA = 0.6f
private val SECONDARY = RiffColors.Ink.copy(alpha = 0.7f)
private val ME_BORDER = 3.dp
private val PODIUM_MAX_WIDTH = 140.dp
private val FIRST_STEP = 96.dp
private val SECOND_STEP = 72.dp
private val THIRD_STEP = 52.dp
private val PLACE_SIZE = 40.sp
private val POSITION_SIZE = 32.sp
private val SCORE_SIZE = 22.sp
