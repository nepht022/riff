package com.rodrigomachado.riff.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.room.Room
import com.rodrigomachado.riff.core.room.RoomDevice
import com.rodrigomachado.riff.core.room.RoomPlayer
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.input.menuKeys
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.core.ui.theme.oswald
import com.rodrigomachado.riff.feature.room.resources.Res
import com.rodrigomachado.riff.feature.room.resources.room_back
import com.rodrigomachado.riff.feature.room.resources.room_code
import com.rodrigomachado.riff.feature.room.resources.room_ended
import com.rodrigomachado.riff.feature.room.resources.room_gone
import com.rodrigomachado.riff.feature.room.resources.room_gone_description
import com.rodrigomachado.riff.feature.room.resources.room_leave
import com.rodrigomachado.riff.feature.room.resources.room_owner
import com.rodrigomachado.riff.feature.room.resources.room_pc
import com.rodrigomachado.riff.feature.room.resources.room_phone
import com.rodrigomachado.riff.feature.room.resources.room_waiting
import com.rodrigomachado.riff.feature.room.resources.room_you
import org.jetbrains.compose.resources.stringResource

/** A sala ao vivo; o Sair (ou o Voltar do sistema) sai da sala e então chama [onLeft]. */
@Composable
fun RoomLobbyScreen(onLeft: () -> Unit, viewModel: RoomLobbyViewModel, code: String, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val left by viewModel.left.collectAsStateWithLifecycle()
    LaunchedEffect(left) { if (left) onLeft() }
    RoomBackHandler(viewModel::leave)
    RoomLobbyContent(state, code, viewModel::leave, modifier)
}

/** O código grande, os jogadores (até 4) e o Sair. A música e o Começar chegam na próxima etapa. */
@Composable
fun RoomLobbyContent(state: RoomLobbyUiState, code: String, onLeave: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().menuKeys(onLeave).verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (state == RoomLobbyUiState.Ended) {
            Ended(onLeave)
            return@Column
        }
        TopTextButton(stringResource(Res.string.room_leave), onLeave)
        CodeHeader(code)
        when (state) {
            RoomLobbyUiState.Loading -> CircularProgressIndicator(
                color = RiffColors.Paper,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            RoomLobbyUiState.NoConnection -> RoomErrorText(RoomError.NO_CONNECTION)

            is RoomLobbyUiState.Ready -> Players(state.room)

            RoomLobbyUiState.Ended -> Unit
        }
    }
}

@Composable
private fun CodeHeader(code: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(Res.string.room_code),
            style = MaterialTheme.typography.titleMedium,
            color = RiffColors.PaperDim,
        )
        Text(
            code,
            style = TextStyle(
                fontFamily = oswald(),
                fontWeight = FontWeight.SemiBold,
                fontSize = CODE_SIZE,
                letterSpacing = CODE_SPACING,
            ),
            color = RiffColors.Paper,
        )
    }
}

@Composable
private fun Players(room: Room) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        room.players.forEach { player ->
            PlayerCard(
                player,
                owner = player.uid == room.owner,
                me = player.uid == room.me,
            )
        }
        if (room.players.size < MAX_PLAYERS) {
            Text(
                stringResource(Res.string.room_waiting),
                style = MaterialTheme.typography.bodyLarge,
                color = RiffColors.PaperDim,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

/**
 * Cartão de papel: nome e aparelho; "Dono" e "sumiu" carimbados; o próprio jogador com a borda vermelha. Quem sumiu
 * fica no papel apagado, com o texto esmaecido (o carimbo continua legível).
 */
@Composable
private fun PlayerCard(player: RoomPlayer, owner: Boolean, me: Boolean) {
    val shape = MaterialTheme.shapes.small
    Row(
        Modifier.fillMaxWidth()
            .background(if (player.gone) RiffColors.PaperDim else RiffColors.Paper, shape)
            .border(ME_BORDER, if (me) RiffColors.TourRed else Color.Transparent, shape)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f).alpha(if (player.gone) GONE_ALPHA else 1f)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(player.name, style = MaterialTheme.typography.titleMedium, color = RiffColors.Ink)
                if (me) {
                    Text(
                        stringResource(Res.string.room_you),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RiffColors.TourRed,
                    )
                }
            }
            Text(
                stringResource(if (player.device == RoomDevice.PC) Res.string.room_pc else Res.string.room_phone),
                style = MaterialTheme.typography.bodyMedium,
                color = RiffColors.Ink.copy(alpha = SECONDARY_ALPHA),
            )
        }
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

/** Carimbo chapado (como o StatusBadge), sem caixa alta: o texto é o que o teste e o leitor de tela leem. */
@Composable
private fun Stamp(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = STAMP_SPACING),
        color = RiffColors.Paper,
        modifier = modifier.background(color).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
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
private val CODE_SIZE = 72.sp
private val CODE_SPACING = 12.sp
private val STAMP_SPACING = 1.5.sp
