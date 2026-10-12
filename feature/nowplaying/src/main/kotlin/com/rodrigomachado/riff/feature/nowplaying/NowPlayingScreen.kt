package com.rodrigomachado.riff.feature.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.NowPlaying
import com.rodrigomachado.riff.core.model.SpotifyError
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.DifficultyDialog
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.ErrorState
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.TrackCard
import com.rodrigomachado.riff.core.ui.component.difficultyLabel
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

/** Etiquetas para testes e para os percursos automáticos de desempenho (`:baselineprofile`). */
object NowPlayingTestTags {
    const val OPEN_LIBRARY = "nowplaying_open_library"
}

/** O que "Jogar" leva para a partida. */
data class PlayRequest(val chartId: String, val difficulty: Difficulty, val trackUri: String, val trackOffsetMs: Int)

@Composable
fun NowPlayingScreen(
    onPlay: (PlayRequest) -> Unit,
    onChooseChart: (trackUri: String, title: String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Voltou ao Riff (por exemplo, depois de instalar ou entrar no Spotify): tenta de novo sozinho.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onReturn() }
    NowPlayingContent(
        state = state,
        onRetry = viewModel::retry,
        onPlay = onPlay,
        onConfirm = viewModel::confirmSuggestion,
        onChooseChart = onChooseChart,
        onOpenLibrary = onOpenLibrary,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
}

@Composable
fun NowPlayingContent(
    state: NowPlayingUiState,
    onRetry: () -> Unit,
    onPlay: (PlayRequest) -> Unit,
    onConfirm: (trackUri: String, chartId: String, confidence: Float) -> Unit,
    onChooseChart: (trackUri: String, title: String) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
) {
    Column(modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        PosterTitle(
            stringResource(R.string.nowplaying_title),
            Modifier.padding(top = Spacing.md),
            style = MaterialTheme.typography.displaySmall,
        )
        Row {
            TextButton(
                onClick = withMenuSound(MenuSound.NAVIGATE, onOpenLibrary),
                modifier = Modifier.testTag(NowPlayingTestTags.OPEN_LIBRARY),
            ) {
                Text(stringResource(R.string.nowplaying_library), color = RiffColors.Paper)
            }
            TextButton(onClick = withMenuSound(MenuSound.NAVIGATE, onOpenSettings)) {
                Text(stringResource(R.string.nowplaying_settings), color = RiffColors.Paper)
            }
        }
        when (state) {
            NowPlayingUiState.Connecting -> LoadingState(message = stringResource(R.string.nowplaying_connecting))

            is NowPlayingUiState.Unavailable -> Unavailable(state.error, onRetry, onOpenLibrary)

            NowPlayingUiState.NothingPlaying -> EmptyState(
                title = stringResource(R.string.nowplaying_nothing_title),
                message = stringResource(R.string.nowplaying_nothing_message),
            )

            is NowPlayingUiState.Playing -> Playing(state.nowPlaying, onPlay, onConfirm, onChooseChart)
        }
    }
}

@Composable
private fun Unavailable(error: SpotifyError, onRetry: () -> Unit, onOpenLibrary: () -> Unit) {
    when (error) {
        SpotifyError.NOT_INSTALLED -> EmptyState(
            title = stringResource(R.string.nowplaying_not_installed_title),
            message = stringResource(R.string.nowplaying_not_installed_message),
            actionLabel = stringResource(R.string.nowplaying_open_library),
            onAction = onOpenLibrary,
            actionSound = MenuSound.NAVIGATE,
        )

        SpotifyError.NOT_CONFIGURED -> EmptyState(
            title = stringResource(R.string.nowplaying_not_configured_title),
            message = stringResource(R.string.nowplaying_not_configured_message),
            actionLabel = stringResource(R.string.nowplaying_open_library),
            onAction = onOpenLibrary,
            actionSound = MenuSound.NAVIGATE,
        )

        else -> ErrorState(stringResource(error.message()), onRetry = onRetry)
    }
}

private fun SpotifyError.message(): Int = when (this) {
    SpotifyError.NOT_LOGGED_IN -> R.string.nowplaying_error_not_logged_in
    SpotifyError.NOT_AUTHORIZED -> R.string.nowplaying_error_not_authorized
    SpotifyError.OFFLINE -> R.string.nowplaying_error_offline
    SpotifyError.DISCONNECTED -> R.string.nowplaying_error_disconnected
    else -> R.string.nowplaying_error_unknown
}

@Composable
private fun ChooseOther(onClick: () -> Unit) {
    TextButton(onClick = withMenuSound(MenuSound.NAVIGATE, onClick)) {
        Text(stringResource(R.string.nowplaying_choose_other), color = RiffColors.Paper)
    }
}

@Composable
private fun Playing(
    nowPlaying: NowPlaying,
    onPlay: (PlayRequest) -> Unit,
    onConfirm: (trackUri: String, chartId: String, confidence: Float) -> Unit,
    onChooseChart: (trackUri: String, title: String) -> Unit,
) {
    val track = nowPlaying.playback
    // Por faixa: se o Spotify trocar de música com o diálogo aberto, ele fecha em vez de juntar mapa e faixa errados.
    // Sobrevive à rotação da tela; o mapa vem do casamento atual da faixa.
    var choosingFor by rememberSaveable(track.trackUri) { mutableStateOf(false) }
    val choosing = (nowPlaying.match as? MatchStatus.Matched)?.takeIf { choosingFor }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        TrackCard(title = track.title, artist = track.artist, tilted = true, badge = { MatchBadge(nowPlaying.match) })
        if (track.isPaused) {
            Text(stringResource(R.string.nowplaying_paused), color = RiffColors.PaperDim)
        }
        when (val match = nowPlaying.match) {
            is MatchStatus.Matched -> {
                Text(
                    stringResource(R.string.nowplaying_chart, match.chart.title, match.chart.artist),
                    color = RiffColors.Paper,
                )
                PosterButton(
                    stringResource(R.string.nowplaying_play),
                    onClick = { choosingFor = true },
                    sound = MenuSound.CONFIRM,
                )
                ChooseOther { onChooseChart(track.trackUri, track.title) }
            }

            is MatchStatus.Suggested -> {
                Text(
                    stringResource(R.string.nowplaying_suggestion, match.chart.title, match.chart.artist),
                    color = RiffColors.Paper,
                )
                PosterButton(
                    stringResource(R.string.nowplaying_confirm),
                    onClick = { onConfirm(track.trackUri, match.chart.id, match.confidence) },
                    sound = MenuSound.CONFIRM,
                )
                ChooseOther { onChooseChart(track.trackUri, track.title) }
            }

            MatchStatus.NoMatch -> {
                Text(stringResource(R.string.nowplaying_no_match), color = RiffColors.Paper)
                PosterButton(
                    stringResource(R.string.nowplaying_choose),
                    onClick = { onChooseChart(track.trackUri, track.title) },
                    sound = MenuSound.NAVIGATE,
                )
            }
        }
    }
    choosing?.let { match ->
        DifficultyDialog(
            title = match.chart.title,
            options = match.chart.difficulties.sorted(),
            label = { stringResource(difficultyLabel(it.ordinal)) },
            onChoose = { difficulty ->
                choosingFor = false
                onPlay(PlayRequest(match.chart.id, difficulty, track.trackUri, match.trackOffsetMs))
            },
            onDismiss = { choosingFor = false },
        )
    }
}

@Composable
private fun MatchBadge(match: MatchStatus) {
    when (match) {
        is MatchStatus.Matched -> StatusBadge(stringResource(R.string.nowplaying_badge_matched), BadgeTone.SUCCESS)
        is MatchStatus.Suggested -> StatusBadge(stringResource(R.string.nowplaying_badge_suggested), BadgeTone.WARNING)
        MatchStatus.NoMatch -> StatusBadge(stringResource(R.string.nowplaying_badge_none), BadgeTone.NEUTRAL)
    }
}
