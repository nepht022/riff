package com.rodrigomachado.riff.feature.nowplaying

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.NowPlaying
import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.ui.component.PlaybackControls
import com.rodrigomachado.riff.core.ui.component.TrackCard
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Largura de Jogar / Escolher mapa / Confirmar: se revezam no mesmo lugar sem os botões pularem. O link ao lado fica no
 * meio do espaço que sobra à direita.
 */
internal val MAIN_BUTTON_WIDTH = 172.dp

/** Largura mínima do Pesquisar no Encore quando fica sozinho na linha de baixo (cresce com a fonte grande). */
internal val LINK_WIDTH = 140.dp

private const val TICK_MS = 500L
private const val SECONDS = 60
private const val TRACK_ALPHA = 0.35f

/** Relógio monotônico da barra de tempo; os screenshots fixam um instante para a imagem não variar. */
internal val LocalElapsedClock = staticCompositionLocalOf<() -> Long> { SystemClock::elapsedRealtime }

/** Anterior, tocar/pausar e próxima no Spotify. */
internal class SongControlActions(
    val onPrevious: () -> Unit,
    val onPlayPause: (isPaused: Boolean) -> Unit,
    val onNext: () -> Unit,
)

/** [top] no alto e [bottom] no pé da tela; numa tela baixa, tudo rola. */
@Composable
internal fun BottomAnchored(top: @Composable ColumnScope.() -> Unit, bottom: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints {
        Column(
            Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(vertical = Spacing.md),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), content = top)
            Column(
                Modifier.padding(top = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                content = bottom,
            )
        }
    }
}

/** A faixa do Spotify, se está pausada, onde a música está e os controles. */
@Composable
internal fun ColumnScope.TrackHeader(nowPlaying: NowPlaying, controls: SongControlActions) {
    val track = nowPlaying.playback
    TrackCard(title = track.title, artist = track.artist, tilted = true, badge = { MatchBadge(nowPlaying.match) })
    if (track.isPaused) {
        Text(stringResource(R.string.nowplaying_paused), color = RiffColors.PaperDim)
    }
    SongTime(track)
    PlaybackControls(
        isPaused = track.isPaused,
        onPrevious = controls.onPrevious,
        onPlayPause = { controls.onPlayPause(track.isPaused) },
        onNext = controls.onNext,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}

/** Barra e tempo da música no Spotify; tocando, anda sozinha entre as leituras do estado do Spotify. */
@Composable
private fun SongTime(track: PlaybackState) {
    val elapsedNow = LocalElapsedClock.current
    var now by remember { mutableLongStateOf(elapsedNow()) }
    if (!track.isPaused) {
        LaunchedEffect(track) {
            while (true) {
                now = elapsedNow()
                delay(TICK_MS)
            }
        }
    }
    val elapsed = if (track.isPaused) 0 else (now - track.capturedAtElapsedMs).coerceAtLeast(0)
    val position = (track.positionMs + elapsed).coerceIn(0, track.durationMs.coerceAtLeast(0))
    val fraction = if (track.durationMs > 0) position.toFloat() / track.durationMs else 0f
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(
            Modifier.fillMaxWidth().height(BAR_HEIGHT).drawBehind {
                drawRect(RiffColors.PaperDim.copy(alpha = TRACK_ALPHA))
                drawRect(RiffColors.TourRed, size = size.copy(width = size.width * fraction))
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clock(position), color = RiffColors.PaperDim, style = MaterialTheme.typography.bodySmall)
            Text(clock(track.durationMs), color = RiffColors.PaperDim, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** "5:55" */
internal fun clock(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / MS_PER_SECOND
    return "${seconds / SECONDS}:${(seconds % SECONDS).toString().padStart(2, '0')}"
}

private const val MS_PER_SECOND = 1_000
private val BAR_HEIGHT = 4.dp
