package com.rodrigomachado.riff.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.room.Room
import com.rodrigomachado.riff.core.room.RoomState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.TrackCard
import com.rodrigomachado.riff.core.ui.component.difficultyName
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.room.resources.Res
import com.rodrigomachado.riff.feature.room.resources.room_be_ready
import com.rodrigomachado.riff.feature.room.resources.room_change_song
import com.rodrigomachado.riff.feature.room.resources.room_checking_song
import com.rodrigomachado.riff.feature.room.resources.room_choose_failed
import com.rodrigomachado.riff.feature.room.resources.room_choose_song
import com.rodrigomachado.riff.feature.room.resources.room_difficulty
import com.rodrigomachado.riff.feature.room.resources.room_download
import com.rodrigomachado.riff.feature.room.resources.room_download_failed
import com.rodrigomachado.riff.feature.room.resources.room_downloading
import com.rodrigomachado.riff.feature.room.resources.room_missing_song
import com.rodrigomachado.riff.feature.room.resources.room_need_two
import com.rodrigomachado.riff.feature.room.resources.room_no_song
import com.rodrigomachado.riff.feature.room.resources.room_not_ready
import com.rodrigomachado.riff.feature.room.resources.room_ready
import com.rodrigomachado.riff.feature.room.resources.room_start
import com.rodrigomachado.riff.feature.room.resources.room_start_with_ready
import com.rodrigomachado.riff.feature.room.resources.room_starting
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
 * A música escolhida (ou "Nenhuma música escolhida") e, para o dono, Escolher/Trocar música e o aviso quando a escolha
 * não foi gravada ([chooseFailed]).
 */
@Composable
internal fun SongSection(room: Room, onChooseSong: () -> Unit, chooseFailed: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val song = room.song
        if (song == null) {
            Text(
                stringResource(Res.string.room_no_song),
                style = MaterialTheme.typography.titleMedium,
                color = RiffColors.PaperDim,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        } else {
            TrackCard(title = song.title, artist = song.artist, tilted = true)
        }
        val lobby = room.state == RoomState.LOBBY
        if (room.iAmOwner && chooseFailed) Note(stringResource(Res.string.room_choose_failed), RiffColors.PaperDim)
        when {
            !room.iAmOwner -> Unit

            // Sem música, escolher é a ação principal do dono; com ela, trocar fica discreto (o Começar vem antes).
            song == null -> PosterButton(
                stringResource(Res.string.room_choose_song),
                onClick = onChooseSong,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enabled = lobby,
                sound = MenuSound.NAVIGATE,
            )

            else -> TextButton(
                onClick = withMenuSound(MenuSound.NAVIGATE, onChooseSong),
                modifier = Modifier.align(Alignment.End).focusHighlight(CircleShape),
                enabled = lobby,
            ) {
                Text(
                    stringResource(Res.string.room_change_song),
                    color = if (lobby) RiffColors.Paper else RiffColors.PaperDim,
                )
            }
        }
    }
}

/** O meu lado da música: conferindo, baixando, sem ela (e o download), ou a dificuldade e o Pronto. */
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

            MySong.Has -> {
                val mineDifficulty = state.me?.difficulty
                DifficultyPicker(song.difficulties.sorted(), mineDifficulty, lobby, actions.onDifficulty)
                val ready = state.me?.ready == true
                PosterButton(
                    stringResource(if (ready) Res.string.room_not_ready else Res.string.room_ready),
                    onClick = actions.onToggleReady,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    enabled = lobby && mineDifficulty != null,
                    sound = MenuSound.CONFIRM,
                )
            }
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
    Text(
        stringResource(Res.string.room_difficulty),
        style = MaterialTheme.typography.titleSmall,
        color = RiffColors.PaperDim,
    )
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
 * O Começar do dono ("com quem está pronto" depois dos 30 s), o aviso (ficar pronto, ou os 2 prontos), ou
 * "Começando…".
 */
@Composable
internal fun StartSection(state: RoomLobbyUiState.Ready, onStart: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (state.starting) {
            Note(stringResource(Res.string.room_starting))
            return@Column
        }
        if (!state.room.iAmOwner) return@Column
        PosterButton(
            stringResource(if (state.withWhoIsReady) Res.string.room_start_with_ready else Res.string.room_start),
            onClick = onStart,
            enabled = state.canStart,
            sound = MenuSound.CONFIRM,
        )
        when (state.startHint) {
            StartHint.BE_READY -> Note(stringResource(Res.string.room_be_ready), RiffColors.PaperDim)
            StartHint.NEED_TWO -> Note(stringResource(Res.string.room_need_two), RiffColors.PaperDim)
            null -> Unit
        }
    }
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
private val CHIP_BORDER = 1.dp
