package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.stage.HighwayGeometry
import com.rodrigomachado.riff.core.ui.stage.StageCache
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.gameplay.resources.Res
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_audio_busy
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_audio_failed
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_back
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_chart_missing
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_chart_unreadable
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_exit
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_folder_lost
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_no_audio
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_paused
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_resume
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_sync
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_sync_earlier
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_sync_later
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_sync_much_earlier
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_sync_much_later
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_unavailable_title
import com.rodrigomachado.riff.feature.gameplay.resources.gameplay_unexpected
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

object GameplayTestTags {
    const val STAGE = "gameplay_stage"
    const val PAUSE = "gameplay_pause"
    const val EXIT = "gameplay_exit"
    const val PROGRESS = "gameplay_progress"
}

internal const val SCRIM_ALPHA = 0.8f
private val PROGRESS_HEIGHT = 4.dp

/** A pista deitada tem a proporção da pista do celular em pé (9:16). */
private const val HIGHWAY_ASPECT = 9f / 16f

/** Véu sobre a imagem do mapa: ela fica de fundo, sem disputar com a pista. */
private const val ART_VEIL = 0.65f
private const val SYNC_STEP_MS = 10
private const val SYNC_BIG_STEP_MS = 1_000

@Composable
fun GameplayScreen(
    onFinished: (scoreId: Long) -> Unit,
    onExit: () -> Unit,
    viewModel: GameplayViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.pause() }
    KeepScreenOn()
    when (val current = state) {
        GameplayUiState.Loading -> LoadingState(modifier)

        is GameplayUiState.Unavailable -> EmptyState(
            title = stringResource(Res.string.gameplay_unavailable_title),
            message = stringResource(current.reason.message()),
            actionLabel = stringResource(Res.string.gameplay_back),
            onAction = onExit,
            modifier = modifier,
        )

        is GameplayUiState.Ready -> GameStage(
            state = current,
            reducedMotion = systemAnimationsReduced(),
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onExit = onExit, // o áudio é liberado em onCleared; a pausa já o silenciou
            onSessionFinished = viewModel::onSessionFinished,
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
    GameBackHandler(enabled = !state.isPaused, onBack = onPause)
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
            }
        }
    }
    val keys = rememberGameKeys(session, state.isPaused, onPause, onResume)
    val highway = @Composable { highwayModifier: Modifier ->
        Canvas(
            highwayModifier
                .testTag(GameplayTestTags.STAGE)
                .lanesByTouch(session, leftHanded),
        ) {
            frame // lê o contador para redesenhar a cada frame
            drawStage(stageCache, session, msPerUnit, leftHanded, reducedMotion)
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().then(keys)) {
        state.art?.let { image ->
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = ART_VEIL)))
        }
        if (!playsByTouch && maxWidth > maxHeight) {
            // Deitada (PC): a pista central com a proporção da do celular, na altura toda; o placar nas laterais.
            val highwayWidth = maxHeight * HIGHWAY_ASPECT
            Box(Modifier.align(Alignment.Center).fillMaxHeight().width(highwayWidth)) {
                highway(Modifier.fillMaxSize())
                JudgmentPop(session, hud, { frame }, reducedMotion, Modifier.align(Alignment.Center))
                SongProgressBar(session, { frame }, Modifier.align(Alignment.BottomCenter).progressBar())
            }
            SideHud(hud, onPause, highwayWidth, Modifier.fillMaxSize())
        } else {
            highway(Modifier.fillMaxSize())
            SongProgressBar(session, { frame }, Modifier.align(Alignment.TopCenter).progressBar())
            Hud(session, hud, { frame }, reducedMotion, onPause, Modifier.fillMaxSize())
        }
        if (state.isPaused) PauseOverlay(state, onAdjustOffset, onResume, onExit)
    }
}

/** Os dedos na pista tocam as trilhas; no PC, o mouse na pista não toca (lá se joga com a palheta). */
private fun Modifier.lanesByTouch(session: GameSession, leftHanded: Boolean) =
    if (playsByTouch) pointerInput(session, leftHanded) { handleLanes(session, leftHanded) } else this

private fun Modifier.progressBar() = fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs)
    .height(PROGRESS_HEIGHT).testTag(GameplayTestTags.PROGRESS)

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
                val lateBy = touchUptimeMs() - change.uptimeMillis
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
private fun PauseOverlay(
    state: GameplayUiState.Ready,
    onAdjustOffset: (Int) -> Unit,
    onResume: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(RiffColors.StageBlack.copy(alpha = SCRIM_ALPHA)),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PosterTitle(stringResource(Res.string.gameplay_paused))
        if (state.audioBusy) {
            Text(
                stringResource(Res.string.gameplay_audio_busy),
                style = MaterialTheme.typography.bodyLarge,
                color = RiffColors.Paper,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                // O TalkBack anuncia o motivo da pausa (o foco some com o Continuar que volta a pausar).
                modifier = Modifier.padding(horizontal = Spacing.lg).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        PosterButton(stringResource(Res.string.gameplay_resume), onClick = onResume, sound = MenuSound.CONFIRM)
        SyncAdjuster(state.songOffsetMs, onAdjustOffset)
        TextButton(onClick = onExit, modifier = Modifier.testTag(GameplayTestTags.EXIT)) {
            Text(stringResource(Res.string.gameplay_exit), color = RiffColors.Paper)
        }
    }
}

/** Ajuste de sincronia desta música: de 1 em 1 s (contagem de entrada) e de 10 em 10 ms (o fino). */
@Composable
private fun SyncAdjuster(songOffsetMs: Int, onAdjustOffset: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(Res.string.gameplay_sync, signed(songOffsetMs)),
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
    Res.string.gameplay_sync_much_earlier to -SYNC_BIG_STEP_MS,
    Res.string.gameplay_sync_earlier to -SYNC_STEP_MS,
    Res.string.gameplay_sync_later to SYNC_STEP_MS,
    Res.string.gameplay_sync_much_later to SYNC_BIG_STEP_MS,
)

private fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()

private fun UnavailableReason.message(): StringResource = when (this) {
    UnavailableReason.NO_AUDIO -> Res.string.gameplay_no_audio
    UnavailableReason.CHART_MISSING -> Res.string.gameplay_chart_missing
    UnavailableReason.CHART_UNREADABLE -> Res.string.gameplay_chart_unreadable
    UnavailableReason.FOLDER_ACCESS_LOST -> Res.string.gameplay_folder_lost
    UnavailableReason.AUDIO_FAILED -> Res.string.gameplay_audio_failed
    UnavailableReason.UNEXPECTED -> Res.string.gameplay_unexpected
}
