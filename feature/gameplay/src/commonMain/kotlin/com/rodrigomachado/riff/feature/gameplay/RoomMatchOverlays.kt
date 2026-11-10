package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.gameplay.resources.Res
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_exit
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_keep_playing
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_leave_message
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_leave_title
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_waiting
import org.jetbrains.compose.resources.stringResource

/** Os segundos da contagem: 3, 2, 1 (o primeiro número nunca passa de 3, mesmo com um começo mais distante). */
private const val COUNTDOWN_FROM = 3L
private const val MS_PER_SECOND = 1_000L

/**
 * Por cima da pista da partida de sala: "Esperando os outros…" enquanto o começo não foi marcado, e depois a contagem
 * 3-2-1 grande até o instante do começo, no relógio da partida (lido a cada quadro: [frame]).
 */
@Composable
internal fun RoomMatchOverlays(state: GameplayUiState.Ready, frame: () -> Long, modifier: Modifier) {
    if (state.waitingForStart) {
        Text(
            stringResource(Res.string.gameplay_waiting),
            style = MaterialTheme.typography.headlineSmall,
            color = RiffColors.Paper,
            textAlign = TextAlign.Center,
            modifier = modifier.padding(Spacing.lg).testTag(GameplayTestTags.WAITING)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        return
    }
    val endsAt = state.countdownEndsAt ?: return
    frame() // a cada quadro: o número muda sozinho
    val leftMs = endsAt - state.session.nowMs()
    if (leftMs <= 0) return
    val seconds = ((leftMs + MS_PER_SECOND - 1) / MS_PER_SECOND).coerceAtMost(COUNTDOWN_FROM)
    PosterTitle(
        seconds.toString(),
        modifier = modifier.testTag(GameplayTestTags.COUNTDOWN),
        style = MaterialTheme.typography.displayLarge,
        heading = false,
    )
}

/**
 * Sair da partida de sala? A música não para (não há pausa na sala): Continuar tocando fecha a pergunta; Sair sai da
 * rodada e volta à sala.
 */
@Composable
internal fun LeaveOverlay(onKeepPlaying: () -> Unit, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(RiffColors.StageBlack.copy(alpha = SCRIM_ALPHA))
            .testTag(GameplayTestTags.LEAVE_PROMPT),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PosterTitle(stringResource(Res.string.gameplay_leave_title))
        Text(
            stringResource(Res.string.gameplay_leave_message),
            style = MaterialTheme.typography.bodyLarge,
            color = RiffColors.Paper,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
        PosterButton(
            stringResource(Res.string.gameplay_keep_playing),
            onClick = onKeepPlaying,
            modifier = Modifier.testTag(GameplayTestTags.KEEP_PLAYING),
            sound = MenuSound.CONFIRM,
        )
        TextButton(onClick = onExit, modifier = Modifier.testTag(GameplayTestTags.EXIT).focusHighlight(CircleShape)) {
            Text(stringResource(Res.string.gameplay_exit), color = RiffColors.Paper)
        }
    }
}

/**
 * Voltar no meio da música pausa (um gesto da borda sem querer não joga a partida fora); na partida de sala, abre e
 * fecha a pergunta de sair ([onToggleLeave]), sem pausar.
 */
@Composable
internal fun MatchBackHandler(inRoom: Boolean, isPaused: Boolean, pause: () -> Unit, onToggleLeave: () -> Unit) {
    if (inRoom) {
        GameBackHandler(enabled = true, onBack = onToggleLeave)
    } else {
        GameBackHandler(enabled = !isPaused, onBack = pause)
    }
}
