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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

object CalibrationTestTags {
    const val TAP_AREA = "calibration_tap_area"
}

@Composable
fun CalibrationScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalibrationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.cancel() }
    CalibrationContent(
        state = state,
        onStart = viewModel::start,
        onTap = viewModel::onTap,
        onNudge = viewModel::nudge,
        onSave = { viewModel.save(onDone) },
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
    Column(modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        TextButton(onClick = onDone) { Text(stringResource(R.string.calibration_back), color = RiffColors.Paper) }
        PosterTitle(stringResource(R.string.calibration_title), style = MaterialTheme.typography.displaySmall)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (state) {
                CalibrationUiState.Instructions -> Message(
                    stringResource(R.string.calibration_instructions),
                    stringResource(R.string.calibration_start),
                    onStart,
                ) {
                    TextButton(onClick = onReset) {
                        Text(stringResource(R.string.calibration_reset), color = RiffColors.Paper)
                    }
                }

                is CalibrationUiState.Measuring -> TapArea(state.taps, onTap)

                is CalibrationUiState.Result -> ResultPanel(state.latencyMs, onNudge, onSave, onStart)

                CalibrationUiState.Unstable -> Message(
                    stringResource(R.string.calibration_unstable),
                    stringResource(R.string.calibration_repeat),
                    onStart,
                )

                CalibrationUiState.AudioFailed -> Message(
                    stringResource(R.string.calibration_audio_failed),
                    stringResource(R.string.calibration_retry),
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
            PosterButton(action, onClick = onAction)
            secondary()
        }
    }
}

/** A tela inteira recebe os toques; o instante é o do evento, no relógio `uptimeMillis`. */
@Composable
private fun TapArea(taps: Int, onTap: (Long) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .testTag(CalibrationTestTags.TAP_AREA)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.filter { it.pressed && !it.previousPressed }.forEach {
                            onTap(it.uptimeMillis)
                            it.consume()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.calibration_measuring),
                style = MaterialTheme.typography.titleMedium,
                color = RiffColors.Paper,
            )
            Text(
                pluralStringResource(R.plurals.calibration_taps, taps, taps),
                style = MaterialTheme.typography.displaySmall,
                color = RiffColors.Paper,
            )
        }
    }
}

@Composable
private fun ResultPanel(latencyMs: Int, onNudge: (Int) -> Unit, onSave: () -> Unit, onRepeat: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(
            stringResource(R.string.calibration_result, latencyMs),
            style = MaterialTheme.typography.headlineSmall,
            color = RiffColors.Paper,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            TextButton(
                onClick = { onNudge(-NUDGE_MS) },
            ) { Text(stringResource(R.string.calibration_less), color = RiffColors.Paper) }
            TextButton(
                onClick = { onNudge(NUDGE_MS) },
            ) { Text(stringResource(R.string.calibration_more), color = RiffColors.Paper) }
        }
        PosterButton(stringResource(R.string.calibration_save), onClick = onSave)
        TextButton(onClick = onRepeat) { Text(stringResource(R.string.calibration_repeat), color = RiffColors.Paper) }
    }
}

private const val NUDGE_MS = 5
