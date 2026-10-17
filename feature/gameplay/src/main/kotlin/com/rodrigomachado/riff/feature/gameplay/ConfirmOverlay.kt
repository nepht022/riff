package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

/** Mapa só sugerido (spec Spotify contínuo §4): confirmar e jogar, escolher outro ou voltar ao menu. */
@Composable
internal fun ConfirmOverlay(
    state: GameplayUiState.Ready,
    onConfirm: () -> Unit,
    onChooseOther: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(RiffColors.StageBlack.copy(alpha = SCRIM_ALPHA)).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PosterTitle(stringResource(R.string.gameplay_confirm_title))
        Text(
            stringResource(R.string.gameplay_confirm_message, state.title, state.artist),
            style = MaterialTheme.typography.bodyLarge,
            color = RiffColors.Paper,
            textAlign = TextAlign.Center,
        )
        PosterButton(stringResource(R.string.gameplay_confirm_play), onClick = onConfirm, sound = MenuSound.CONFIRM)
        TextButton(onClick = withMenuSound(MenuSound.NAVIGATE, onChooseOther)) {
            Text(stringResource(R.string.gameplay_confirm_other), color = RiffColors.Paper)
        }
        TextButton(onClick = withMenuSound(MenuSound.BACK, onExit)) {
            Text(stringResource(R.string.gameplay_confirm_back), color = RiffColors.Paper)
        }
    }
}
