package com.rodrigomachado.riff.feature.gameplay

import android.provider.Settings.Global
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.stage.HighwayGeometry
import com.rodrigomachado.riff.core.ui.stage.JudgmentText
import com.rodrigomachado.riff.core.ui.stage.MultiplierStamp
import com.rodrigomachado.riff.core.ui.stage.ScoreText
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import kotlinx.coroutines.isActive

object GameplayTestTags {
    const val STAGE = "gameplay_stage"
}

private const val JUDGMENT_VISIBLE_MS = 500L
private const val JUDGMENT_BOUNCE_MS = 120f
private const val JUDGMENT_BOUNCE_SCALE = 0.25f
private const val COMBO_MIN_TO_SHOW = 10
private const val SCRIM_ALPHA = 0.8f

@Composable
fun GameplayScreen(
    onFinished: (scoreId: Long) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GameplayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.pause() }
    KeepScreenOn()
    when (val current = state) {
        GameplayUiState.Loading -> LoadingState(modifier)

        is GameplayUiState.Unavailable -> EmptyState(
            title = stringResource(R.string.gameplay_unavailable_title),
            message = stringResource(current.reason.message()),
            actionLabel = stringResource(R.string.gameplay_back),
            onAction = onExit,
            modifier = modifier,
        )

        is GameplayUiState.Ready -> GameStage(
            state = current,
            reducedMotion = rememberReducedMotion(),
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onExit = onExit, // o áudio é liberado em onCleared; a pausa já o silenciou
            onSessionFinished = viewModel::onSessionFinished,
            onPlaybackChanged = viewModel::onPlaybackChanged,
            modifier = modifier,
        )

        is GameplayUiState.Finished -> LaunchedEffect(current.scoreId) { onFinished(current.scoreId) }
    }
}

/**
 * Palco da partida. [animate] liga o loop de frames (os screenshots desligam para congelar um instante).
 */
@Composable
internal fun GameStage(
    state: GameplayUiState.Ready,
    reducedMotion: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onExit: () -> Unit,
    onSessionFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onPlaybackChanged: (Boolean) -> Unit = {},
    animate: Boolean = true,
) {
    val session = state.session
    val leftHanded = state.settings.leftHanded
    val msPerUnit = BASE_MS_PER_DISTANCE / state.settings.noteSpeed
    var frame by remember { mutableLongStateOf(0L) }
    // Voltar no meio da música pausa (um gesto da borda sem querer não joga a partida fora).
    BackHandler(enabled = !state.isPaused, onBack = onPause)
    val currentPaused by rememberUpdatedState(state.isPaused)
    val playbackChanged by rememberUpdatedState(onPlaybackChanged)
    if (animate) {
        LaunchedEffect(session) {
            while (isActive) {
                withFrameMillis {
                    session.tick()
                    frame++
                }
                if (session.isFinished) {
                    onSessionFinished()
                    break
                }
                // Pausa ou retomada fora do jogo (notificação do Spotify): a tela acompanha a música.
                if (session.isPaused != currentPaused) playbackChanged(session.isPaused)
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .testTag(GameplayTestTags.STAGE)
                .pointerInput(session, leftHanded) { handleLanes(session, leftHanded) },
        ) {
            frame // lê o contador para redesenhar a cada frame
            drawStage(session, msPerUnit, leftHanded, reducedMotion)
        }
        Hud(session, frame, reducedMotion, onPause, Modifier.fillMaxSize())
        if (state.isPaused) PauseOverlay(onResume, onExit)
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.handleLanes(
    session: GameSession,
    leftHanded: Boolean,
) {
    val lanes = mutableMapOf<PointerId, Int>()
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val geometry = HighwayGeometry(Size(size.width.toFloat(), size.height.toFloat()))
            for (change in event.changes) {
                if (change.changedToDown()) {
                    val lane = screenLane(geometry.laneAt(change.position.x), leftHanded)
                    lanes[change.id] = lane
                    session.press(lane)
                    change.consume()
                } else if (change.changedToUp()) {
                    lanes.remove(change.id)?.let(session::release)
                    change.consume()
                }
            }
        }
    }
}

@Composable
private fun Hud(session: GameSession, frame: Long, reducedMotion: Boolean, onPause: () -> Unit, modifier: Modifier) {
    frame // recompõe o placar a cada frame
    val engine = session.engine
    Box(modifier.padding(Spacing.md)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            ScoreText(engine.score)
            TextButton(onClick = onPause) { Text(stringResource(R.string.gameplay_pause), color = RiffColors.Paper) }
            MultiplierStamp(engine.multiplier)
        }
        val combo = engine.state.combo
        if (combo >= COMBO_MIN_TO_SHOW) {
            Text(
                stringResource(R.string.gameplay_combo, combo),
                style = MaterialTheme.typography.labelLarge,
                color = RiffColors.Paper,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.xl * 2),
            )
        }
        val age = session.judgmentAgeMs()
        val judgment = session.lastJudgment
        if (judgment != null && age < JUDGMENT_VISIBLE_MS) {
            val bounce = if (reducedMotion) {
                1f
            } else {
                1f +
                    JUDGMENT_BOUNCE_SCALE * (
                        1f - (age / JUDGMENT_BOUNCE_MS).coerceAtMost(
                            1f,
                        )
                        )
            }
            JudgmentText(
                stringResource(
                    if (judgment ==
                        Judgment.PERFECT
                    ) {
                        R.string.gameplay_judgment_perfect
                    } else {
                        R.string.gameplay_judgment_good
                    },
                ),
                Modifier.align(Alignment.Center).graphicsLayer {
                    scaleX = bounce
                    scaleY = bounce
                },
            )
        }
    }
}

@Composable
private fun PauseOverlay(onResume: () -> Unit, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(RiffColors.StageBlack.copy(alpha = SCRIM_ALPHA)),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PosterTitle(stringResource(R.string.gameplay_paused))
        PosterButton(stringResource(R.string.gameplay_resume), onClick = onResume)
        TextButton(onClick = onExit) { Text(stringResource(R.string.gameplay_exit), color = RiffColors.Paper) }
    }
}

/** "Remover animações" do Android: escala de animação zerada. */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember { Global.getFloat(context.contentResolver, Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

private fun UnavailableReason.message(): Int = when (this) {
    UnavailableReason.NO_AUDIO -> R.string.gameplay_no_audio
    UnavailableReason.CHART_MISSING -> R.string.gameplay_chart_missing
    UnavailableReason.FOLDER_ACCESS_LOST -> R.string.gameplay_folder_lost
    UnavailableReason.AUDIO_FAILED -> R.string.gameplay_audio_failed
    UnavailableReason.SPOTIFY_UNAVAILABLE -> R.string.gameplay_unavailable_spotify
}
