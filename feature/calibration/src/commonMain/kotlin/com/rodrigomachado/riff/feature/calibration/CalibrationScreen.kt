package com.rodrigomachado.riff.feature.calibration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.input.OnWindowFocusLost
import com.rodrigomachado.riff.core.ui.input.lateByMs
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.calibration.resources.Res
import com.rodrigomachado.riff.feature.calibration.resources.calibration_audio_failed
import com.rodrigomachado.riff.feature.calibration.resources.calibration_back
import com.rodrigomachado.riff.feature.calibration.resources.calibration_instructions
import com.rodrigomachado.riff.feature.calibration.resources.calibration_instructions_keys
import com.rodrigomachado.riff.feature.calibration.resources.calibration_less
import com.rodrigomachado.riff.feature.calibration.resources.calibration_measuring
import com.rodrigomachado.riff.feature.calibration.resources.calibration_measuring_keys
import com.rodrigomachado.riff.feature.calibration.resources.calibration_more
import com.rodrigomachado.riff.feature.calibration.resources.calibration_repeat
import com.rodrigomachado.riff.feature.calibration.resources.calibration_reset
import com.rodrigomachado.riff.feature.calibration.resources.calibration_result
import com.rodrigomachado.riff.feature.calibration.resources.calibration_retry
import com.rodrigomachado.riff.feature.calibration.resources.calibration_save
import com.rodrigomachado.riff.feature.calibration.resources.calibration_start
import com.rodrigomachado.riff.feature.calibration.resources.calibration_taps
import com.rodrigomachado.riff.feature.calibration.resources.calibration_title
import com.rodrigomachado.riff.feature.calibration.resources.calibration_unstable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

object CalibrationTestTags {
    const val TAP_AREA = "calibration_tap_area"
}

@Composable
fun CalibrationScreen(
    onDone: () -> Unit,
    viewModel: CalibrationViewModel,
    modifier: Modifier = Modifier,
    /** Depois de salvar (a volta já soou como confirmação); por padrão, o mesmo que [onDone]. */
    onSaved: () -> Unit = onDone,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Sair do app (celular) ou da janela (PC, o Alt+Tab) no meio da medição para o metrônomo e volta ao começo.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.cancel() }
    OnWindowFocusLost { viewModel.cancel() }
    CalibrationContent(
        state = state,
        onStart = viewModel::start,
        onTap = viewModel::onTap,
        onNudge = viewModel::nudge,
        onSave = { viewModel.save(onSaved) },
        onDone = onDone,
        onReset = { viewModel.reset(onDone) },
        modifier = modifier,
    )
}

@Composable
fun CalibrationContent(
    state: CalibrationUiState,
    onStart: () -> Unit,
    onTap: (Long) -> Unit,
    onNudge: (Int) -> Unit,
    onSave: () -> Unit,
    onDone: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val instructions =
        if (tapsByTouch) Res.string.calibration_instructions else Res.string.calibration_instructions_keys
    Column(modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        TextButton(onClick = onDone) { Text(stringResource(Res.string.calibration_back), color = RiffColors.Paper) }
        PosterTitle(stringResource(Res.string.calibration_title), style = MaterialTheme.typography.displaySmall)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (state) {
                CalibrationUiState.Instructions -> Message(
                    stringResource(instructions),
                    stringResource(Res.string.calibration_start),
                    onStart,
                ) {
                    TextButton(onClick = onReset) {
                        Text(stringResource(Res.string.calibration_reset), color = RiffColors.Paper)
                    }
                }

                is CalibrationUiState.Measuring -> TapArea(state.taps, onTap)

                is CalibrationUiState.Result -> ResultPanel(state.latencyMs, onNudge, onSave, onStart)

                CalibrationUiState.Unstable -> Message(
                    stringResource(Res.string.calibration_unstable),
                    stringResource(Res.string.calibration_repeat),
                    onStart,
                )

                CalibrationUiState.AudioFailed -> Message(
                    stringResource(Res.string.calibration_audio_failed),
                    stringResource(Res.string.calibration_retry),
                    onStart,
                )
            }
        }
    }
}

@Composable
private fun Message(text: String, action: String, onAction: () -> Unit, secondary: @Composable () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = RiffColors.Paper)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PosterButton(action, onClick = onAction, sound = MenuSound.CONFIRM)
            secondary()
        }
    }
}

/**
 * A tela inteira recebe os toques; o instante é o do evento, no relógio [calibrationClockMs]. No PC, as teclas
 * ([rememberTapKeys]).
 */
@Composable
private fun TapArea(taps: Int, onTap: (Long) -> Unit) {
    val input = if (tapsByTouch) Modifier.touchTaps(onTap) else rememberTapKeys(onTap)
    val measuring = if (tapsByTouch) Res.string.calibration_measuring else Res.string.calibration_measuring_keys
    Box(
        Modifier
            .fillMaxSize()
            .testTag(CalibrationTestTags.TAP_AREA)
            .then(input),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(measuring),
                style = MaterialTheme.typography.titleMedium,
                color = RiffColors.Paper,
            )
            Text(
                pluralStringResource(Res.plurals.calibration_taps, taps, taps),
                style = MaterialTheme.typography.displaySmall,
                color = RiffColors.Paper,
            )
        }
    }
}

/** Cada dedo que desce é um toque, no instante do evento (`uptimeMillis`, o relógio da calibração no celular). */
private fun Modifier.touchTaps(onTap: (Long) -> Unit) = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.filter { it.pressed && !it.previousPressed }.forEach {
                onTap(it.uptimeMillis)
                it.consume()
            }
        }
    }
}

/**
 * As teclas como toques (PC): a área pega o foco ao aparecer e cada tecla que desce é um toque, no instante em que
 * foi apertada. O Esc não conta (fica para quem está em volta), e a repetição automática de uma tecla segurada
 * também não: só a primeira descida de cada tecla.
 */
@Composable
private fun rememberTapKeys(onTap: (Long) -> Unit): Modifier {
    val tap by rememberUpdatedState(onTap)
    val down = remember { mutableSetOf<Key>() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    return Modifier
        .onKeyEvent { event ->
            when {
                event.key == Key.Escape -> false

                event.type == KeyEventType.KeyDown -> {
                    if (down.add(event.key)) tap(calibrationClockMs() - event.lateByMs())
                    true
                }

                event.type == KeyEventType.KeyUp -> down.remove(event.key)

                else -> false
            }
        }
        .focusRequester(focus)
        .focusTarget()
}

@Composable
private fun ResultPanel(latencyMs: Int, onNudge: (Int) -> Unit, onSave: () -> Unit, onRepeat: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(
            stringResource(Res.string.calibration_result, latencyMs),
            style = MaterialTheme.typography.headlineSmall,
            color = RiffColors.Paper,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            TextButton(
                onClick = withMenuSound(MenuSound.NAVIGATE) { onNudge(-NUDGE_MS) },
            ) { Text(stringResource(Res.string.calibration_less), color = RiffColors.Paper) }
            TextButton(
                onClick = withMenuSound(MenuSound.NAVIGATE) { onNudge(NUDGE_MS) },
            ) { Text(stringResource(Res.string.calibration_more), color = RiffColors.Paper) }
        }
        PosterButton(stringResource(Res.string.calibration_save), onClick = onSave, sound = MenuSound.CONFIRM)
        TextButton(
            onClick = withMenuSound(MenuSound.CONFIRM, onRepeat),
        ) { Text(stringResource(Res.string.calibration_repeat), color = RiffColors.Paper) }
    }
}

private const val NUDGE_MS = 5
