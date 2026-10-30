package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.R
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

/**
 * Anterior · tocar/pausar · próxima, comandando o Spotify. Ícones desenhados em código; o do meio, maior e vermelho,
 * mostra "tocar" quando [isPaused]. [inMatch] (a pausa da partida): tocar soa como o Continuar, trocar de música fica
 * em silêncio (a volta ao Tocando agora já soa) e o leitor de tela avisa que trocar encerra a partida.
 */
@Composable
fun PlaybackControls(
    isPaused: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    inMatch: Boolean = false,
) {
    val skipSound = MenuSound.NAVIGATE.takeUnless { inMatch }
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlButton(
            stringResource(if (inMatch) R.string.playback_previous_ends_match else R.string.playback_previous),
            SIDE,
            onPrevious,
            skipSound,
        ) { drawSkip(forward = false) }
        ControlButton(
            stringResource(if (isPaused) R.string.playback_play else R.string.playback_pause),
            MIDDLE,
            onPlayPause,
            if (inMatch) MenuSound.CONFIRM else MenuSound.NAVIGATE,
            highlighted = true,
        ) { if (isPaused) drawPlay() else drawPause() }
        ControlButton(
            stringResource(if (inMatch) R.string.playback_next_ends_match else R.string.playback_next),
            SIDE,
            onNext,
            skipSound,
        ) { drawSkip(forward = true) }
    }
}

@Composable
private fun ControlButton(
    description: String,
    size: Dp,
    onClick: () -> Unit,
    sound: MenuSound?,
    highlighted: Boolean = false,
    icon: DrawScope.() -> Unit,
) {
    Box(
        Modifier.size(size)
            .then(if (highlighted) Modifier.background(RiffColors.TourRed, CircleShape) else Modifier)
            // O efeito do toque fica redondo, como o botão.
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = sound?.let { withMenuSound(it, onClick) } ?: onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(ICON)) { icon() }
    }
}

private fun DrawScope.drawPlay() {
    val w = size.width
    drawPath(
        Path().apply {
            moveTo(w * 0.22f, 0f)
            lineTo(w, size.height / 2)
            lineTo(w * 0.22f, size.height)
            close()
        },
        RiffColors.Paper,
    )
}

private fun DrawScope.drawPause() {
    val bar = Size(size.width * 0.3f, size.height)
    drawRect(RiffColors.Paper, Offset(size.width * 0.1f, 0f), bar)
    drawRect(RiffColors.Paper, Offset(size.width * 0.6f, 0f), bar)
}

/** Triângulo apontando para o lado e a barra no fim, como ⏭ (ou ⏮ espelhado). */
private fun DrawScope.drawSkip(forward: Boolean) {
    val w = size.width
    val h = size.height
    fun x(fraction: Float) = if (forward) w * fraction else w * (1 - fraction)
    drawPath(
        Path().apply {
            moveTo(x(0.05f), 0f)
            lineTo(x(0.78f), h / 2)
            lineTo(x(0.05f), h)
            close()
        },
        RiffColors.Paper,
    )
    val barLeft = if (forward) w * 0.78f else w * 0.05f
    drawRect(RiffColors.Paper, Offset(barLeft, 0f), Size(w * 0.17f, h))
}

private val SIDE = 48.dp
private val MIDDLE = 56.dp
private val ICON = 20.dp
