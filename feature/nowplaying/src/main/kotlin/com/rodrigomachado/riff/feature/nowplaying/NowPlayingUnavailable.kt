package com.rodrigomachado.riff.feature.nowplaying

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
import com.rodrigomachado.riff.core.model.SpotifyError
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

/** Sem o Spotify instalado, ou sem a permissão para ver o que ele toca. */
@Composable
internal fun Unavailable(error: SpotifyError, onAllow: () -> Unit, onOpenLibrary: () -> Unit) {
    when (error) {
        SpotifyError.NOT_INSTALLED -> EmptyState(
            title = stringResource(R.string.nowplaying_not_installed_title),
            message = stringResource(R.string.nowplaying_not_installed_message),
            actionLabel = stringResource(R.string.nowplaying_open_library),
            onAction = onOpenLibrary,
            actionSound = MenuSound.NAVIGATE,
        )

        // Sem a permissão, o Riff não vê o Spotify; a Biblioteca joga sem ela (pedido do dono).
        SpotifyError.NO_PERMISSION -> NeedsPermission(onAllow, onOpenLibrary)
    }
}

/** O pedido de "Acesso às notificações", com a Biblioteca logo abaixo como alternativa. */
@Composable
private fun NeedsPermission(onAllow: () -> Unit, onOpenLibrary: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.nowplaying_permission_title),
            style = MaterialTheme.typography.titleLarge,
            color = RiffColors.Paper,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.nowplaying_permission_message),
            style = MaterialTheme.typography.bodyMedium,
            color = RiffColors.PaperDim,
            textAlign = TextAlign.Center,
        )
        PosterButton(stringResource(R.string.nowplaying_allow), onClick = onAllow, sound = MenuSound.CONFIRM)
        TextButton(onClick = withMenuSound(MenuSound.NAVIGATE, onOpenLibrary)) {
            Text(stringResource(R.string.nowplaying_or_library), color = RiffColors.Paper, textAlign = TextAlign.Center)
        }
    }
}
