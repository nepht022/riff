package com.rodrigomachado.riff.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.room.Room
import com.rodrigomachado.riff.core.room.RoomDevice
import com.rodrigomachado.riff.core.room.RoomPlayer
import com.rodrigomachado.riff.core.room.SongStatus
import com.rodrigomachado.riff.core.ui.art.ChartArtImages
import com.rodrigomachado.riff.core.ui.component.KeyHintBar
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.difficultyName
import com.rodrigomachado.riff.core.ui.input.LocalGamepad
import com.rodrigomachado.riff.core.ui.input.menuKeys
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.room.resources.Res
import com.rodrigomachado.riff.feature.room.resources.room_back
import com.rodrigomachado.riff.feature.room.resources.room_downloading
import com.rodrigomachado.riff.feature.room.resources.room_ended
import com.rodrigomachado.riff.feature.room.resources.room_gone
import com.rodrigomachado.riff.feature.room.resources.room_gone_description
import com.rodrigomachado.riff.feature.room.resources.room_owner
import com.rodrigomachado.riff.feature.room.resources.room_pc
import com.rodrigomachado.riff.feature.room.resources.room_phone
import com.rodrigomachado.riff.feature.room.resources.room_players
import com.rodrigomachado.riff.feature.room.resources.room_stamp_choosing
import com.rodrigomachado.riff.feature.room.resources.room_stamp_missing
import com.rodrigomachado.riff.feature.room.resources.room_stamp_ready
import com.rodrigomachado.riff.feature.room.resources.room_waiting
import com.rodrigomachado.riff.feature.room.resources.room_you
import org.jetbrains.compose.resources.stringResource

/**
 * A sala ao vivo; o Sair (ou o Voltar do sistema) sai da sala e então chama [onLeft]. O dono escolhe a música pela
 * Biblioteca ([onChooseSong], que devolve ao [RoomLobbyViewModel.chooseSong]); a contagem de uma rodada em que estou
 * pronto abre a partida ([onMatch]).
 */
@Composable
fun RoomLobbyScreen(
    onLeft: () -> Unit,
    viewModel: RoomLobbyViewModel,
    code: String,
    modifier: Modifier = Modifier,
    onChooseSong: () -> Unit = {},
    onMatch: (MatchTicket) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val left by viewModel.left.collectAsStateWithLifecycle()
    val match by viewModel.navigateToMatch.collectAsStateWithLifecycle()
    LaunchedEffect(left) { if (left) onLeft() }
    LaunchedEffect(match) {
        match?.let {
            onMatch(it)
            viewModel.onMatchOpened()
        }
    }
    RoomBackHandler(viewModel::leave)
    val actions = RoomLobbyActions(
        onChooseSong = onChooseSong,
        onDifficulty = viewModel::chooseDifficulty,
        onToggleReady = viewModel::toggleReady,
        onDownload = viewModel::download,
        onStart = viewModel::start,
    )
    RoomLobbyContent(state, code, viewModel::leave, modifier, actions, viewModel.images)
}

/**
 * A sala, de cima para baixo: "Sair da sala", o código pequeno e o compartilhar; os jogadores (até 4); a música (com
 * a capa, de [images]) e a minha dificuldade; a linha do que falta; e, fixos embaixo, Pronto e o Começar do dono lado
 * a lado. No PC (janela larga) a coluna fica no meio e, com o `LocalGamepad`, a barra de teclas vai no rodapé.
 */
@Composable
fun RoomLobbyContent(
    state: RoomLobbyUiState,
    code: String,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    actions: RoomLobbyActions = RoomLobbyActions(),
    images: ChartArtImages? = null,
) {
    val pad = LocalGamepad.current
    BoxWithConstraints(modifier.fillMaxSize().menuKeys(onLeave)) {
        // O widthIn vem antes do fillMaxWidth: estica até o máximo do PC, nunca além.
        val width = if (maxWidth >= WIDE_MIN_WIDTH) Modifier.widthIn(max = WIDE_CONTENT) else Modifier
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).align(Alignment.CenterHorizontally).then(width).fillMaxWidth()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (state == RoomLobbyUiState.Ended) {
                    Ended(onLeave)
                } else {
                    // Com fonte grande, os botões fixos tomariam a tela: vão para o fim da rolagem, um sobre o outro.
                    val pinned = LocalDensity.current.fontScale < LARGE_FONT_SCALE
                    LobbyTop(code, onLeave)
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    ) {
                        LobbyBody(state, actions, images)
                        if (!pinned && state is RoomLobbyUiState.Ready) LobbyButtons(state, actions, stacked = true)
                    }
                    if (pinned && state is RoomLobbyUiState.Ready) LobbyButtons(state, actions)
                }
            }
            if (pad != null) {
                val connected by pad.connected.collectAsState()
                KeyHintBar(roomKeys(), connected, Modifier.testTag(RoomTestTags.KEY_HINTS))
            }
        }
    }
}

/** O meio da sala (o que rola): carregando, sem conexão, ou os jogadores, a música e a linha do que falta. */
@Composable
private fun ColumnScope.LobbyBody(state: RoomLobbyUiState, actions: RoomLobbyActions, images: ChartArtImages?) {
    when (state) {
        RoomLobbyUiState.Loading -> CircularProgressIndicator(
            color = RiffColors.Paper,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        RoomLobbyUiState.NoConnection -> RoomErrorText(RoomError.NO_CONNECTION)

        is RoomLobbyUiState.Ready -> {
            Players(state.room)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SongSection(state.room, actions.onChooseSong, state.chooseFailed, images, state.mySong)
                MySongSection(state, actions)
            }
            MissingLine(state, Modifier.fillMaxWidth())
        }

        RoomLobbyUiState.Ended -> Unit
    }
}

@Composable
private fun Players(room: Room) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionLabel(stringResource(Res.string.room_players, room.players.size, MAX_PLAYERS))
        room.players.forEach { player ->
            PlayerCard(
                player,
                owner = player.uid == room.owner,
                me = player.uid == room.me,
                withSong = room.song != null,
            )
        }
        if (room.players.size < MAX_PLAYERS) {
            Text(
                stringResource(Res.string.room_waiting),
                style = MaterialTheme.typography.bodyLarge,
                color = RiffColors.PaperDim,
            )
        }
    }
}

/**
 * Cartão de papel: nome, aparelho e (com a música escolhida, [withSong]) a dificuldade; "Dono", "sumiu" e o estado
 * para a música (Pronto, Escolhendo, Baixando…, Sem a música) carimbados; o próprio jogador com a borda vermelha.
 * Quem sumiu fica no papel apagado, com o texto esmaecido (o carimbo continua legível).
 */
@Composable
private fun PlayerCard(player: RoomPlayer, owner: Boolean, me: Boolean, withSong: Boolean) {
    val shape = MaterialTheme.shapes.small
    // Com fonte grande, os carimbos descem para baixo do nome em vez de espremê-lo.
    FlowRow(
        Modifier.fillMaxWidth()
            .background(if (player.gone) RiffColors.PaperDim else RiffColors.Paper, shape)
            .border(ME_BORDER, if (me) RiffColors.TourRed else Color.Transparent, shape)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Column(Modifier.padding(end = Spacing.sm).alpha(if (player.gone) GONE_ALPHA else 1f)) {
            FlowRow(
                itemVerticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(player.name, style = MaterialTheme.typography.titleMedium, color = RiffColors.Ink)
                if (me) {
                    Text(
                        stringResource(Res.string.room_you),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RiffColors.TourRed,
                    )
                }
            }
            val device = stringResource(
                if (player.device == RoomDevice.PC) Res.string.room_pc else Res.string.room_phone,
            )
            val difficulty = player.difficulty?.takeIf { withSong }?.let { difficultyName(it.ordinal) }
            Text(
                listOfNotNull(device, difficulty).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = RiffColors.Ink.copy(alpha = SECONDARY_ALPHA),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            if (withSong && !player.gone) SongStamp(player)
            if (player.gone) {
                val description = stringResource(Res.string.room_gone_description, player.name)
                Stamp(
                    stringResource(Res.string.room_gone),
                    RiffColors.StampWarning,
                    Modifier.semantics { contentDescription = description },
                )
            }
            if (owner) Stamp(stringResource(Res.string.room_owner), RiffColors.TourRed)
        }
    }
}

/** O estado do jogador para a música da sala. */
@Composable
private fun SongStamp(player: RoomPlayer) {
    when {
        player.ready && player.song == SongStatus.HAS && player.difficulty != null ->
            Stamp(stringResource(Res.string.room_stamp_ready), RiffColors.StampSuccess)

        player.song == SongStatus.MISSING -> Stamp(stringResource(Res.string.room_stamp_missing), RiffColors.StampError)

        player.song == SongStatus.DOWNLOADING ->
            Stamp(stringResource(Res.string.room_downloading), RiffColors.StampWarning)

        else -> Stamp(stringResource(Res.string.room_stamp_choosing), RiffColors.Ink)
    }
}

@Composable
private fun Ended(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            stringResource(Res.string.room_ended),
            style = MaterialTheme.typography.titleLarge,
            color = RiffColors.Paper,
        )
        PosterButton(stringResource(Res.string.room_back), onClick = onBack)
    }
}

private const val MAX_PLAYERS = 4
private const val GONE_ALPHA = 0.6f
private const val SECONDARY_ALPHA = 0.7f
private val ME_BORDER = 3.dp
private val WIDE_MIN_WIDTH = 800.dp

/** A partir desta escala da fonte do sistema, Pronto e Começar deixam de ficar fixos embaixo. */
private const val LARGE_FONT_SCALE = 1.3f

/** A coluna da sala no PC: a mesma largura da Biblioteca. */
private val WIDE_CONTENT = 760.dp
