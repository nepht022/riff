package com.rodrigomachado.riff.feature.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.data.queue.QueueMiss
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.NowPlaying
import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyError
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.DifficultyDialog
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.ErrorState
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.SeparationIndicator
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.difficultyLabel
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import kotlinx.coroutines.flow.first

/** Etiquetas para testes e para os percursos automáticos de desempenho (`:baselineprofile`). */
object NowPlayingTestTags {
    const val OPEN_LIBRARY = "nowplaying_open_library"
    const val QUEUE_MISS = "nowplaying_queue_miss"
}

/** O que "Jogar" leva para a partida; [confirmFirst]: o mapa é só sugestão e a partida abre pedindo confirmação. */
data class PlayRequest(
    val chartId: String,
    val difficulty: Difficulty,
    val trackUri: String,
    val trackOffsetMs: Int,
    val confirmFirst: Boolean = false,
)

@Composable
fun NowPlayingScreen(
    onPlay: (PlayRequest) -> Unit,
    onChooseChart: (trackUri: String, title: String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** Pesquisar no Encore um mapa para a faixa (sem mapa ou só com sugestão). */
    onSearch: (PlaybackState) -> Unit = {},
    onOpenReport: () -> Unit = {},
    /** O estado salvo desta tela na navegação, onde a partida deixa o pedido de começar a próxima música. */
    autoPlayRequests: SavedStateHandle? = null,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val autoPlay by viewModel.autoPlay.collectAsStateWithLifecycle()
    LaunchedEffect(autoPlayRequests) { autoPlayRequests?.let(viewModel::watchAutoPlay) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.cancelAutoPlay() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(autoPlay) {
        val request = when (val action = autoPlay) {
            is NowPlayingAutoPlay.Action.Start -> action.request
            is NowPlayingAutoPlay.Action.Confirm -> action.request
            else -> return@LaunchedEffect
        }
        // A tela acabou de voltar da partida: a navegação só aceita a próxima quando ela estiver de volta à frente.
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        viewModel.autoPlayHandled()
        onPlay(request)
    }
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
        onSearch = onSearch,
        onOpenReport = onOpenReport,
        onPrevious = viewModel::previous,
        onPlayPause = viewModel::playPause,
        onNext = viewModel::next,
        askDifficultyFor = (autoPlay as? NowPlayingAutoPlay.Action.ChooseDifficulty)?.trackUri,
        onDifficultyAsked = viewModel::autoPlayHandled,
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
    onSearch: (PlaybackState) -> Unit = {},
    onOpenReport: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onPlayPause: (isPaused: Boolean) -> Unit = {},
    onNext: () -> Unit = {},
    askDifficultyFor: String? = null,
    onDifficultyAsked: () -> Unit = {},
) {
    Column(modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        PosterTitle(
            stringResource(R.string.nowplaying_title),
            Modifier.padding(top = Spacing.md),
            style = MaterialTheme.typography.displaySmall,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = withMenuSound(MenuSound.NAVIGATE, onOpenLibrary),
                modifier = Modifier.testTag(NowPlayingTestTags.OPEN_LIBRARY),
            ) {
                Text(stringResource(R.string.nowplaying_library), color = RiffColors.Paper)
            }
            TextButton(onClick = withMenuSound(MenuSound.NAVIGATE, onOpenSettings)) {
                Text(stringResource(R.string.nowplaying_settings), color = RiffColors.Paper)
            }
            TextButton(onClick = withMenuSound(MenuSound.NAVIGATE, onOpenReport)) {
                Text(stringResource(R.string.nowplaying_report), color = RiffColors.Paper)
            }
            Spacer(Modifier.weight(1f))
            SeparationIndicator()
        }
        when (state) {
            NowPlayingUiState.Connecting -> LoadingState(message = stringResource(R.string.nowplaying_connecting))

            is NowPlayingUiState.Unavailable -> Unavailable(state.error, onRetry, onOpenLibrary)

            NowPlayingUiState.NothingPlaying -> EmptyState(
                title = stringResource(R.string.nowplaying_nothing_title),
                message = stringResource(R.string.nowplaying_nothing_message),
            )

            is NowPlayingUiState.Playing -> Playing(
                state.nowPlaying,
                state.queueMiss,
                state.preparingMap,
                onPlay,
                onConfirm,
                onChooseChart,
                onSearch,
                SongControlActions(onPrevious, onPlayPause, onNext),
                askDifficultyFor,
                onDifficultyAsked,
            )
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

/** Na linha do botão principal, ocupa o espaço livre à direita e fica no meio dele. */
@Composable
private fun RowScope.ChooseOther(onClick: () -> Unit) {
    TextButton(onClick = withMenuSound(MenuSound.NAVIGATE, onClick), modifier = Modifier.weight(1f)) {
        Text(stringResource(R.string.nowplaying_choose_other), color = RiffColors.Paper, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NoMatchActions(queueMiss: QueueMiss?, preparingMap: Boolean, onChoose: () -> Unit, onSearch: () -> Unit) {
    ChartText(stringResource(R.string.nowplaying_no_match))
    val note = if (preparingMap) R.string.nowplaying_queue_preparing else queueMiss?.message()
    note?.let {
        Text(
            stringResource(it),
            Modifier.fillMaxWidth().testTag(NowPlayingTestTags.QUEUE_MISS),
            style = MaterialTheme.typography.bodyMedium,
            color = RiffColors.PaperDim,
            textAlign = TextAlign.Center,
        )
    }
    ActionRow {
        PosterButton(
            stringResource(R.string.nowplaying_choose),
            onClick = onChoose,
            sound = MenuSound.NAVIGATE,
            minWidth = MAIN_BUTTON_WIDTH,
        )
        SearchEncore(onSearch, Modifier.weight(1f))
    }
}

/** O texto do mapa acima dos botões, no meio da tela. */
@Composable
private fun ChartText(text: String) {
    Text(text, Modifier.fillMaxWidth(), color = RiffColors.Paper, textAlign = TextAlign.Center)
}

/** O botão principal à esquerda e a alternativa no meio do espaço que sobra. */
@Composable
private fun ActionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun SearchEncore(onClick: () -> Unit, modifier: Modifier = Modifier.width(LINK_WIDTH)) {
    TextButton(onClick = withMenuSound(MenuSound.NAVIGATE, onClick), modifier = modifier) {
        Text(stringResource(R.string.nowplaying_search), color = RiffColors.Paper, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Playing(
    nowPlaying: NowPlaying,
    queueMiss: QueueMiss?,
    preparingMap: Boolean,
    onPlay: (PlayRequest) -> Unit,
    onConfirm: (trackUri: String, chartId: String, confidence: Float) -> Unit,
    onChooseChart: (trackUri: String, title: String) -> Unit,
    onSearch: (PlaybackState) -> Unit,
    controls: SongControlActions,
    askDifficultyFor: String?,
    onDifficultyAsked: () -> Unit,
) {
    val track = nowPlaying.playback
    // Por faixa: se o Spotify trocar de música com o diálogo aberto, ele fecha em vez de juntar mapa e faixa errados.
    // Sobrevive à rotação da tela; o mapa vem do casamento atual da faixa.
    var choosingFor by rememberSaveable(track.trackUri) { mutableStateOf(false) }
    val choosing = (nowPlaying.match as? MatchStatus.Matched)?.takeIf { choosingFor }
    // Voltou de uma partida para uma música cujo mapa não tem a dificuldade de antes: abre a escolha.
    LaunchedEffect(askDifficultyFor, track.trackUri) {
        if (askDifficultyFor == track.trackUri) {
            choosingFor = true
            onDifficultyAsked()
        }
    }
    // A faixa e os controles no alto; o mapa e o botão principal no pé da tela, onde o polegar alcança.
    BottomAnchored(top = { TrackHeader(nowPlaying, controls) }) {
        when (val match = nowPlaying.match) {
            is MatchStatus.Matched -> {
                ChartText(stringResource(R.string.nowplaying_chart, match.chart.title, match.chart.artist))
                ActionRow {
                    PosterButton(
                        stringResource(R.string.nowplaying_play),
                        onClick = { choosingFor = true },
                        sound = MenuSound.CONFIRM,
                        minWidth = MAIN_BUTTON_WIDTH,
                    )
                    ChooseOther { onChooseChart(track.trackUri, track.title) }
                }
            }

            is MatchStatus.Suggested -> {
                ChartText(stringResource(R.string.nowplaying_suggestion, match.chart.title, match.chart.artist))
                ActionRow {
                    PosterButton(
                        stringResource(R.string.nowplaying_confirm),
                        onClick = { onConfirm(track.trackUri, match.chart.id, match.confidence) },
                        sound = MenuSound.CONFIRM,
                        minWidth = MAIN_BUTTON_WIDTH,
                    )
                    ChooseOther { onChooseChart(track.trackUri, track.title) }
                }
                SearchEncore({ onSearch(track) })
            }

            MatchStatus.NoMatch -> NoMatchActions(
                queueMiss,
                preparingMap,
                { onChooseChart(track.trackUri, track.title) },
                { onSearch(track) },
            )
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
internal fun MatchBadge(match: MatchStatus) {
    when (match) {
        is MatchStatus.Matched -> StatusBadge(stringResource(R.string.nowplaying_badge_matched), BadgeTone.SUCCESS)
        is MatchStatus.Suggested -> StatusBadge(stringResource(R.string.nowplaying_badge_suggested), BadgeTone.WARNING)
        MatchStatus.NoMatch -> StatusBadge(stringResource(R.string.nowplaying_badge_none), BadgeTone.NEUTRAL)
    }
}
