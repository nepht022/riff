package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.flow.first

/**
 * A partida acabou e o Riff segue para a próxima música. A navegação só aceita com a tela à frente: com a cortina
 * de notificações aberta (o ⏭ dali), espera ela fechar em vez de perder o pedido e deixar a tela vazia.
 */
@Composable
internal fun NextSongEffect(
    next: GameplayUiState.NextSong,
    onNextSong: (difficulty: Difficulty, fromTrackUri: String, waitMs: Long) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val callback by rememberUpdatedState(onNextSong)
    LaunchedEffect(next) {
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        callback(next.difficulty, next.fromTrackUri, next.waitMs)
    }
}
