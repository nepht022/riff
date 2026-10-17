package com.rodrigomachado.riff.feature.gameplay

import android.os.SystemClock
import android.provider.Settings.Global
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.stage.HighwayGeometry
import com.rodrigomachado.riff.core.ui.stage.JudgmentText
import com.rodrigomachado.riff.core.ui.stage.MultiplierStamp
import com.rodrigomachado.riff.core.ui.stage.ScoreText
import com.rodrigomachado.riff.core.ui.stage.StageCache
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import kotlinx.coroutines.isActive

object GameplayTestTags {
    const val STAGE = "gameplay_stage"
    const val PAUSE = "gameplay_pause"
    const val EXIT = "gameplay_exit"
    const val PROGRESS = "gameplay_progress"
}

private const val COMBO_MIN_TO_SHOW = 10
private const val SCRIM_ALPHA = 0.8f
private val PROGRESS_HEIGHT = 4.dp

/** Véu sobre a imagem do mapa: ela fica de fundo, sem disputar com a pista. */
private const val ART_VEIL = 0.65f
private const val SYNC_STEP_MS = 10
private const val SYNC_BIG_STEP_MS = 1_000

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
            onAdjustOffset = viewModel::adjustSongOffset,
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
    onAdjustOffset: (Int) -> Unit = {},
    animate: Boolean = true,
) {
    val session = state.session
    val leftHanded = state.settings.leftHanded
    val msPerUnit = BASE_MS_PER_DISTANCE / state.settings.noteSpeed
    var frame by remember { mutableLongStateOf(0L) }
    val look = state.settings.trackLook
    val stageCache = remember(look) { StageCache().also { it.look = look } }
    // Primeiro quadro (e screenshots, com animate = false); depois o loop atualiza a cada quadro.
    val hud = remember(session) { HudState().also { it.update(session, state.withoutSeparateGuitar) } }
    val withoutSeparateGuitar by rememberUpdatedState(state.withoutSeparateGuitar)
    // Voltar no meio da música pausa (um gesto da borda sem querer não joga a partida fora).
    BackHandler(enabled = !state.isPaused, onBack = onPause)
    val currentPaused by rememberUpdatedState(state.isPaused)
    val playbackChanged by rememberUpdatedState(onPlaybackChanged)
    if (animate) {
        LaunchedEffect(session) {
            while (isActive) {
                withFrameMillis {
                    session.tick()
                    hud.update(session, withoutSeparateGuitar)
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
        state.art?.let { image ->
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = ART_VEIL)))
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .testTag(GameplayTestTags.STAGE)
                .pointerInput(session, leftHanded) { handleLanes(session, leftHanded) },
        ) {
            frame // lê o contador para redesenhar a cada frame
            drawStage(stageCache, session, msPerUnit, leftHanded, reducedMotion)
        }
        SongProgressBar(
            session,
            { frame },
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs)
                .height(PROGRESS_HEIGHT).testTag(GameplayTestTags.PROGRESS),
        )
        Hud(session, hud, { frame }, reducedMotion, onPause, Modifier.fillMaxSize())
        if (state.isPaused) PauseOverlay(state.songOffsetMs, onAdjustOffset, onResume, onExit)
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.handleLanes(
    session: GameSession,
    leftHanded: Boolean,
) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val geometry = HighwayGeometry(Size(size.width.toFloat(), size.height.toFloat()))
            for (change in event.changes) {
                // Quanto o toque esperou até chegar aqui: o julgamento usa o instante em que o dedo encostou ou saiu.
                val lateBy = SystemClock.uptimeMillis() - change.uptimeMillis
                if (change.changedToDown()) {
                    val lane = screenLane(geometry.laneAt(change.position.x), leftHanded)
                    session.pressPointer(change.id.value, lane, lateBy)
                    change.consume()
                } else if (change.changedToUp()) {
                    session.releasePointer(change.id.value, lateBy)
                    change.consume()
                }
            }
        }
    }
}

@Composable
private fun Hud(
    session: GameSession,
    hud: HudState,
    frame: () -> Long,
    reducedMotion: Boolean,
    onPause: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.padding(Spacing.md)) {
        // Cada um no seu canto: o placar crescendo não empurra o Pausar, que fica sempre no centro.
        ScoreText(hud.score, Modifier.align(Alignment.TopStart))
        TextButton(
            onClick = onPause,
            modifier = Modifier.align(Alignment.TopCenter).testTag(GameplayTestTags.PAUSE),
        ) {
            Text(stringResource(R.string.gameplay_pause), color = RiffColors.Paper)
        }
        MultiplierStamp(hud.multiplier, Modifier.align(Alignment.TopEnd))
        if (hud.combo >= COMBO_MIN_TO_SHOW) {
            Text(
                stringResource(R.string.gameplay_combo, hud.combo),
                style = MaterialTheme.typography.labelLarge,
                color = RiffColors.Paper,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.xl * 2),
            )
        }
        val judgment = hud.judgment
        if (judgment != null && hud.showingJudgment) {
            val label = if (judgment == Judgment.PERFECT) {
                R.string.gameplay_judgment_perfect
            } else {
                R.string.gameplay_judgment_good
            }
            JudgmentText(
                stringResource(label),
                Modifier.align(Alignment.Center).graphicsLayer {
                    frame() // relê a cada quadro só esta camada, sem recompor o placar
                    val bounce = judgmentBounce(session.judgmentAgeMs(), reducedMotion)
                    scaleX = bounce
                    scaleY = bounce
                },
            )
        }
        if (hud.showingNotice) {
            NoSeparateGuitarNotice(Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xl * 3))
        }
    }
}

/** As faixas separadas falharam: a partida segue com um arquivo só (spec da guitarra separada §4). */
@Composable
private fun NoSeparateGuitarNotice(modifier: Modifier) {
    Text(
        stringResource(R.string.gameplay_no_separate_guitar),
        style = MaterialTheme.typography.bodyMedium,
        color = RiffColors.PaperDim,
        modifier = modifier,
    )
}

@Composable
private fun PauseOverlay(songOffsetMs: Int, onAdjustOffset: (Int) -> Unit, onResume: () -> Unit, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(RiffColors.StageBlack.copy(alpha = SCRIM_ALPHA)),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PosterTitle(stringResource(R.string.gameplay_paused))
        PosterButton(stringResource(R.string.gameplay_resume), onClick = onResume, sound = MenuSound.CONFIRM)
        SyncAdjuster(songOffsetMs, onAdjustOffset)
        TextButton(onClick = onExit, modifier = Modifier.testTag(GameplayTestTags.EXIT)) {
            Text(stringResource(R.string.gameplay_exit), color = RiffColors.Paper)
        }
    }
}

/** Ajuste de sincronia desta música: de 1 em 1 s (contagem de entrada) e de 10 em 10 ms (o fino). */
@Composable
private fun SyncAdjuster(songOffsetMs: Int, onAdjustOffset: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.gameplay_sync, signed(songOffsetMs)),
            style = MaterialTheme.typography.bodyLarge,
            color = RiffColors.Paper,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for ((label, deltaMs) in SYNC_BUTTONS) {
                TextButton(onClick = withMenuSound(MenuSound.NAVIGATE) { onAdjustOffset(deltaMs) }) {
                    Text(stringResource(label), color = RiffColors.Paper)
                }
            }
        }
    }
}

private val SYNC_BUTTONS = listOf(
    R.string.gameplay_sync_much_earlier to -SYNC_BIG_STEP_MS,
    R.string.gameplay_sync_earlier to -SYNC_STEP_MS,
    R.string.gameplay_sync_later to SYNC_STEP_MS,
    R.string.gameplay_sync_much_later to SYNC_BIG_STEP_MS,
)

private fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()

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
    UnavailableReason.CHART_UNREADABLE -> R.string.gameplay_chart_unreadable
    UnavailableReason.FOLDER_ACCESS_LOST -> R.string.gameplay_folder_lost
    UnavailableReason.AUDIO_FAILED -> R.string.gameplay_audio_failed
    UnavailableReason.SPOTIFY_UNAVAILABLE -> R.string.gameplay_unavailable_spotify
    UnavailableReason.UNEXPECTED -> R.string.gameplay_unexpected
}
