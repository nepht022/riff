package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import com.rodrigomachado.riff.core.ui.theme.RiffColors

private const val TRACK_ALPHA = 0.35f

/** Quanto da música já passou: trilho apagado e a parte tocada em vermelho. Redesenha a cada quadro sem recompor. */
@Composable
internal fun SongProgressBar(session: GameSession, frame: () -> Long, modifier: Modifier) {
    Box(
        modifier.drawBehind {
            frame()
            drawRect(RiffColors.PaperDim.copy(alpha = TRACK_ALPHA))
            drawRect(RiffColors.TourRed, size = size.copy(width = size.width * session.progress))
        },
    )
}
