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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
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
import com.rodrigomachado.riff.core.model.ControlAction
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.input.menuKeys
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
    const val RESUME = "gameplay_resume"
    const val PAUSE_MENU = "gameplay_pause_menu"
    const val PROGRESS = "gameplay_progress"
    const val COUNTDOWN = "gameplay_countdown"
    const val WAITING = "gameplay_waiting"
    const val LEAVE_PROMPT = "gameplay_leave_prompt"
    const val KEEP_PLAYING = "gameplay_keep_playing"
    const val LIVE_BOARD = "gameplay_live_board"

    /** A chave da minha linha no placar ao vivo ([liveLine]). */
    const val LIVE_ME = "me"

    /** A linha do jogador [uid] no placar ao vivo (a minha: [LIVE_ME]). */
    fun liveLine(uid: String) = "gameplay_live_line_$uid"
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
    // O placar dos outros (só na partida de sala): o estado vai inteiro até o bloco, que é o único a lê-lo.
    val liveOthers = viewModel.liveOthers?.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.pause() }
    KeepScreenOn()
    // Na partida de sala, Sair também avisa a sala (saí da rodada); sozinho, só fecha a tela.
    val exit = remember(viewModel, onExit) {
        {
            viewModel.leave()
            onExit()
        }
    }
    when (val current = state) {
        GameplayUiState.Loading -> LoadingState(modifier)

        is GameplayUiState.Unavailable -> UnavailableScreen(current.reason, exit, modifier)

        is GameplayUiState.Ready -> GameStage(
            state = current,
            reducedMotion = systemAnimationsReduced(),
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onExit = exit, // o áudio é liberado em onCleared; a pausa já o silenciou
            onSessionFinished = viewModel::onSessionFinished,
            onAdjustOffset = viewModel::adjustSongOffset,
            onFrame = viewModel::onFrame,
            modifier = modifier,
            liveOthers = liveOthers,
        )

        is GameplayUiState.Finished -> LaunchedEffect(current.scoreId) { onFinished(current.scoreId) }
    }
}

/** "Não dá para jogar": o motivo e o Voltar; no PC, as setas e o Esc (volta) como nos menus. */
@Composable
internal fun UnavailableScreen(reason: UnavailableReason, onExit: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(Res.string.gameplay_unavailable_title),
        message = stringResource(reason.message()),
        actionLabel = stringResource(Res.string.gameplay_back),
        onAction = onExit,
        modifier = modifier.menuKeys(onBack = onExit),
    )
}

/**
 * Palco da partida. [animate] liga o loop de frames (os screenshots desligam para congelar um instante). Na partida de
 * sala ([GameplayUiState.Ready.inRoom]) não há pausa: nem o botão, nem a tecla, nem a perda do foco; o Voltar pergunta
 * se é para sair, com a música seguindo, e a contagem 3-2-1 fica por cima da pista até o começo. [liveOthers] (só na
 * partida de sala) liga o placar ao vivo minimizado, com o dos outros; [onFrame] roda depois de cada quadro avançar a
 * partida (a publicação do placar ao vivo).
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
    liveOthers: State<List<LiveScore>>? = null,
    onFrame: () -> Unit = {},
) {
    val session = state.session
    // A pausa já no instante em que muda (o teclado e o controle a leem antes de a tela recompor).
    val inRoom = state.inRoom
    val matchPause = rememberMatchPause(state.isPaused, onPause, onResume, pausable = !inRoom)
    val leftHanded = state.settings.leftHanded
    val msPerUnit = BASE_MS_PER_DISTANCE / state.settings.noteSpeed
    var frame by remember { mutableLongStateOf(0L) }
    val look = state.settings.trackLook
    val stageCache = remember(look) { StageCache().also { it.look = look } }
    // Primeiro quadro (e screenshots, com animate = false); depois o loop atualiza a cada quadro.
    val hud = remember(session) { HudState().also { it.update(session, state.withoutSeparateGuitar) } }
    MatchBackHandler(inRoom, state.isPaused, matchPause.pause) { matchPause.leaving = !matchPause.leaving }
    val pauseButton = if (inRoom) null else matchPause.pause
    val afterFrame by rememberUpdatedState(onFrame)
    if (animate) {
        FrameLoop(session, hud, state.withoutSeparateGuitar, onSessionFinished) {
            frame++
            afterFrame()
        }
    }
    val pauseMenu = remember { PauseMenu() }
    val keys = rememberGameKeys(session, state.settings.controls, matchPause, state.isPaused, pauseMenu)
    // O controle passa pela mesma sessão: a perda do foco (no teclado acima) solta as cores dele também.
    HandleGamepad(session, matchPause, state.settings.controls.pad[ControlAction.PAUSE])
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
            SideHud(hud, pauseButton, highwayWidth, Modifier.fillMaxSize(), reducedMotion, liveOthers)
        } else {
            highway(Modifier.fillMaxSize())
            SongProgressBar(session, { frame }, Modifier.align(Alignment.TopCenter).progressBar())
            Hud(session, hud, { frame }, reducedMotion, pauseButton, Modifier.fillMaxSize(), liveOthers)
        }
        if (state.isPaused) PauseOverlay(state, pauseMenu, onAdjustOffset, matchPause.resume, onExit)
        if (inRoom) {
            RoomMatchOverlays(state, { frame }, Modifier.align(Alignment.Center))
            if (matchPause.leaving) {
                LeaveOverlay(pauseMenu, onKeepPlaying = { matchPause.leaving = false }, onExit = onExit)
            }
        }
    }
}

/** O loop de quadros: avança a partida e o placar a cada quadro ([onFrame]) e avisa quando ela acaba. */
@Composable
private fun FrameLoop(
    session: GameSession,
    hud: HudState,
    withoutSeparateGuitar: Boolean,
    onSessionFinished: () -> Unit,
    onFrame: () -> Unit,
) {
    val noSeparateGuitar by rememberUpdatedState(withoutSeparateGuitar)
    val finished by rememberUpdatedState(onSessionFinished)
    LaunchedEffect(session) {
        while (isActive) {
            withFrameMillis {
                session.tick()
                hud.update(session, noSeparateGuitar)
                onFrame()
            }
            if (session.isFinished) {
                finished()
                break
            }
        }
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
    menu: PauseMenu,
    onAdjustOffset: (Int) -> Unit,
    onResume: () -> Unit,
    onExit: () -> Unit,
) {
    // A pausa fechou com um botão focado: a próxima começa sem foco (a primeira seta foca o Continuar).
    DisposableEffect(menu) { onDispose { menu.hasFocus = false } }
    Column(
        Modifier.fillMaxSize().background(RiffColors.StageBlack.copy(alpha = SCRIM_ALPHA))
            .testTag(GameplayTestTags.PAUSE_MENU)
            .pauseMenu(menu),
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
        PosterButton(
            stringResource(Res.string.gameplay_resume),
            onClick = onResume,
            modifier = Modifier.testTag(GameplayTestTags.RESUME).focusRequester(menu.first),
            sound = MenuSound.CONFIRM,
        )
        SyncAdjuster(state.songOffsetMs, onAdjustOffset)
        TextButton(onClick = onExit, modifier = Modifier.testTag(GameplayTestTags.EXIT).focusHighlight(CircleShape)) {
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
                TextButton(
                    onClick = withMenuSound(MenuSound.NAVIGATE) { onAdjustOffset(deltaMs) },
                    modifier = Modifier.focusHighlight(CircleShape),
                ) {
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
