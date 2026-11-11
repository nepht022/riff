package com.rodrigomachado.riff.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.room.Room
import com.rodrigomachado.riff.core.room.RoomSong
import com.rodrigomachado.riff.core.room.RoomState
import com.rodrigomachado.riff.core.ui.art.ChartArtImages
import com.rodrigomachado.riff.core.ui.component.ChartThumb
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.difficultyName
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.room.resources.Res
import com.rodrigomachado.riff.feature.room.resources.room_change_song
import com.rodrigomachado.riff.feature.room.resources.room_checking_song
import com.rodrigomachado.riff.feature.room.resources.room_choose_failed
import com.rodrigomachado.riff.feature.room.resources.room_choose_song
import com.rodrigomachado.riff.feature.room.resources.room_difficulty
import com.rodrigomachado.riff.feature.room.resources.room_download
import com.rodrigomachado.riff.feature.room.resources.room_download_failed
import com.rodrigomachado.riff.feature.room.resources.room_downloading
import com.rodrigomachado.riff.feature.room.resources.room_missing_song
import com.rodrigomachado.riff.feature.room.resources.room_no_song
import com.rodrigomachado.riff.feature.room.resources.room_not_ready
import com.rodrigomachado.riff.feature.room.resources.room_ready
import com.rodrigomachado.riff.feature.room.resources.room_song
import com.rodrigomachado.riff.feature.room.resources.room_start
import org.jetbrains.compose.resources.stringResource

/** Os botões da sala da M2 (o Sair continua à parte, como na M1). */
data class RoomLobbyActions(
    /** O dono abre a Biblioteca no modo de escolha. */
    val onChooseSong: () -> Unit = {},
    val onDifficulty: (Difficulty) -> Unit = {},
    val onToggleReady: () -> Unit = {},
    val onDownload: () -> Unit = {},
    val onStart: () -> Unit = {},
)

/**
 * A música escolhida num cartão de papel (capa, título e artista; para o dono, "Trocar") ou "Nenhuma música
 * escolhida" (para o dono, "Escolher música"), e o aviso quando a escolha não foi gravada ([chooseFailed]). A capa é
 * pedida de novo quando [mySong] muda (o convidado que baixou a música passa a ter a imagem).
 */
@Composable
internal fun SongSection(
    room: Room,
    onChooseSong: () -> Unit,
    chooseFailed: Boolean = false,
    images: ChartArtImages? = null,
    mySong: MySong? = null,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionLabel(stringResource(Res.string.room_song))
        val song = room.song
        val lobby = room.state == RoomState.LOBBY
        if (song == null) {
            Text(
                stringResource(Res.string.room_no_song),
                style = MaterialTheme.typography.titleMedium,
                color = RiffColors.PaperDim,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            // Sem música, escolher é a ação principal do dono.
            if (room.iAmOwner) {
                PosterButton(
                    stringResource(Res.string.room_choose_song),
                    onClick = onChooseSong,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    enabled = lobby,
                    sound = MenuSound.NAVIGATE,
                )
            }
        } else {
            SongCard(song, onChooseSong.takeIf { room.iAmOwner }, lobby, images, mySong)
        }
        if (room.iAmOwner && chooseFailed) Note(stringResource(Res.string.room_choose_failed), RiffColors.PaperDim)
    }
}

/** O cartão da música: capa, título e artista em tinta sobre o papel; com [onChange] (o dono), o "Trocar". */
@Composable
private fun SongCard(
    song: RoomSong,
    onChange: (() -> Unit)?,
    enabled: Boolean,
    images: ChartArtImages?,
    mySong: MySong?,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(RiffColors.Paper, MaterialTheme.shapes.small)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Pede de novo quando a música chega (não a cada passo do download: antes dela, não dá para procurar).
        ChartThumb(song.chartId, song.title, COVER, images, refresh = mySong == MySong.Has)
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                color = RiffColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = RiffColors.Ink.copy(alpha = SECONDARY_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onChange != null) {
            TextButton(
                onClick = withMenuSound(MenuSound.NAVIGATE, onChange),
                modifier = Modifier.heightIn(min = MIN_TOUCH).focusHighlight(CircleShape),
                enabled = enabled,
            ) {
                Text(
                    stringResource(Res.string.room_change_song),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) RiffColors.TourRed else RiffColors.Ink.copy(alpha = DISABLED_ALPHA),
                )
            }
        }
    }
}

/** O meu lado da música: conferindo, baixando, sem ela (e o download), ou a minha dificuldade. */
@Composable
internal fun MySongSection(state: RoomLobbyUiState.Ready, actions: RoomLobbyActions) {
    val song = state.room.song ?: return
    val lobby = state.room.state == RoomState.LOBBY
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        when (val mine = state.mySong ?: MySong.Checking) {
            MySong.Checking -> Note(stringResource(Res.string.room_checking_song))

            is MySong.Downloading -> {
                Note(stringResource(Res.string.room_downloading))
                LinearProgressIndicator(
                    progress = { mine.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = RiffColors.TourRed,
                    trackColor = RiffColors.PaperDim,
                )
            }

            is MySong.Missing -> {
                Note(stringResource(Res.string.room_missing_song))
                if (mine.failed) Note(stringResource(Res.string.room_download_failed), RiffColors.PaperDim)
                if (mine.canDownload) {
                    PosterButton(
                        stringResource(Res.string.room_download),
                        onClick = actions.onDownload,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        enabled = lobby,
                    )
                }
            }

            MySong.Has ->
                DifficultyPicker(song.difficulties.sorted(), state.me?.difficulty, lobby, actions.onDifficulty)
        }
    }
}

/** A dificuldade entre as da música: uma fileira de etiquetas, a escolhida em papel. */
@Composable
private fun DifficultyPicker(
    options: List<Difficulty>,
    selected: Difficulty?,
    enabled: Boolean,
    onChoose: (Difficulty) -> Unit,
) {
    SectionLabel(stringResource(Res.string.room_difficulty))
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        options.forEach { difficulty ->
            val chosen = difficulty == selected
            val shape = MaterialTheme.shapes.small
            Box(
                Modifier.weight(1f)
                    .heightIn(min = 48.dp)
                    .alpha(if (enabled) 1f else DISABLED_ALPHA)
                    .background(if (chosen) RiffColors.Paper else RiffColors.StageBlack, shape)
                    .border(CHIP_BORDER, if (chosen) RiffColors.Paper else RiffColors.PaperDim, shape)
                    .focusHighlight(shape)
                    .selectable(
                        selected = chosen,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onChoose(difficulty) },
                    )
                    .padding(Spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    difficultyName(difficulty.ordinal),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (chosen) RiffColors.Ink else RiffColors.Paper,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Embaixo, lado a lado: o meu Pronto / "Não estou pronto" (com a música: só depois da dificuldade) à esquerda e o
 * Começar do dono à direita (na contagem, some: a linha diz "Começando…"). Sem nenhum dos dois, nada. [stacked]: um
 * sobre o outro, na largura toda (fonte grande).
 */
@Composable
internal fun LobbyButtons(state: RoomLobbyUiState.Ready, actions: RoomLobbyActions, stacked: Boolean = false) {
    val showReady = state.room.song != null && state.mySong == MySong.Has
    val showStart = state.room.iAmOwner && !state.starting
    if (!showReady && !showStart) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // O cartaz vermelho só cresce pela largura mínima: ela é a parte de cada um (menos a sombra).
        val count = if (stacked || !(showReady && showStart)) 1 else 2
        val share = (maxWidth - Spacing.md * (count - 1)) / count - BUTTON_SHADOW
        val buttons: @Composable (Modifier) -> Unit = {
            LobbyButtonPair(state, actions, ButtonsShown(showReady, showStart), it, share)
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                buttons(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                buttons(Modifier.weight(1f))
            }
        }
    }
}

/** Quais dos dois botões de baixo aparecem. */
private class ButtonsShown(val ready: Boolean, val start: Boolean)

/**
 * Os botões de baixo, cada um com [each] (o peso na fileira, ou a largura toda na pilha) e o cartaz com a largura
 * mínima [width].
 */
@Composable
private fun LobbyButtonPair(
    state: RoomLobbyUiState.Ready,
    actions: RoomLobbyActions,
    shown: ButtonsShown,
    each: Modifier,
    width: Dp,
) {
    val lobby = state.room.state == RoomState.LOBBY
    if (shown.ready) {
        val ready = state.me?.ready == true
        val enabled = lobby && state.me?.difficulty != null
        val text = stringResource(if (ready) Res.string.room_not_ready else Res.string.room_ready)
        // Pronto é o chamado (vermelho); depois dele, desfazer fica em papel, sem disputar com o Começar.
        if (ready) {
            PaperButton(text, actions.onToggleReady, enabled, each.fillMaxHeight())
        } else {
            PosterButton(
                text,
                onClick = actions.onToggleReady,
                modifier = each,
                enabled = enabled,
                sound = MenuSound.CONFIRM,
                minWidth = width,
            )
        }
    }
    if (shown.start) {
        PosterButton(
            stringResource(Res.string.room_start),
            onClick = actions.onStart,
            modifier = each,
            enabled = state.canStart,
            sound = MenuSound.CONFIRM,
            minWidth = width,
        )
    }
}

/** O botão secundário de pôster: papel com texto em tinta e sombra dura, sem inclinação. */
@Composable
private fun PaperButton(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    Box(
        modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(end = HardShadow, bottom = HardShadow)
            .drawBehind { drawRect(RiffColors.Ink, Offset(HardShadow.toPx(), HardShadow.toPx()), size) }
            .background(RiffColors.Paper)
            .focusHighlight(RectangleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = withMenuSound(MenuSound.CONFIRM, onClick))
            .defaultMinSize(minHeight = MIN_TOUCH)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = RiffColors.Ink, textAlign = TextAlign.Center)
    }
}

/** O rótulo apagado de cada parte da sala ("Jogadores · 3 de 4", "Música", "Sua dificuldade"). */
@Composable
internal fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = RiffColors.PaperDim)
}

@Composable
private fun Note(text: String, color: Color = RiffColors.Paper) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val DISABLED_ALPHA = 0.45f
private const val SECONDARY_ALPHA = 0.7f
private val CHIP_BORDER = 1.dp
private val COVER = 56.dp
private val MIN_TOUCH = 48.dp

/** A sombra do cartaz (a do PosterButton), fora da largura mínima. */
private val BUTTON_SHADOW = 5.dp
