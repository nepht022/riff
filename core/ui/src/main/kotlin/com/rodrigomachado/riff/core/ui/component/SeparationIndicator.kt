package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.SeparationFailure
import com.rodrigomachado.riff.core.model.SeparationStatus
import com.rodrigomachado.riff.core.ui.R
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.ceil
import kotlin.math.roundToInt

/** A separação da guitarra em segundo plano, fornecida pelo app (as telas só põem o ícone). */
interface SeparationControls {
    val status: StateFlow<SeparationStatus>

    fun cancel()

    fun dismissFailure()
}

val LocalSeparation = staticCompositionLocalOf<SeparationControls?> { null }

object SeparationIndicatorTags {
    const val ICON = "separation_icon"
}

/** O ícone da separação no topo da tela; some quando não há separação. */
@Composable
fun SeparationIndicator(modifier: Modifier = Modifier) {
    val controls = LocalSeparation.current ?: return
    val status by controls.status.collectAsState()
    SeparationIndicatorContent(status, controls::cancel, controls::dismissFailure, modifier)
}

@Composable
fun SeparationIndicatorContent(
    status: SeparationStatus,
    onCancel: () -> Unit,
    onDismissFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status == SeparationStatus.Idle) return
    var open by remember { mutableStateOf(false) }
    val description = stringResource(R.string.ui_separation_open)
    IconButton(
        onClick = withMenuSound(MenuSound.NAVIGATE) { open = true },
        modifier = modifier.testTag(SeparationIndicatorTags.ICON).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (status is SeparationStatus.Running) {
                CircularProgressIndicator(
                    progress = { status.progress },
                    modifier = Modifier.size(RING),
                    color = RiffColors.Paper,
                    trackColor = RiffColors.PaperDim,
                )
            }
            Pick(failed = status is SeparationStatus.Failed)
        }
    }
    if (open) SeparationPanel(status, onCancel, onDismissFailure) { open = false }
}

@Composable
private fun SeparationPanel(
    status: SeparationStatus,
    onCancel: () -> Unit,
    onDismissFailure: () -> Unit,
    onClose: () -> Unit,
) {
    when (status) {
        is SeparationStatus.Running -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(status.title) },
            text = { Column { runningText(status).forEach { Text(it) } } },
            confirmButton = {
                TextButton(
                    onClick = withMenuSound(MenuSound.BACK) {
                        onClose()
                        onCancel()
                    },
                ) { Text(stringResource(R.string.ui_separation_cancel)) }
            },
        )

        is SeparationStatus.Failed -> AlertDialog(
            onDismissRequest = onClose,
            text = { Text(failedText(status)) },
            confirmButton = {
                TextButton(
                    onClick = withMenuSound(MenuSound.CONFIRM) {
                        onClose()
                        onDismissFailure()
                    },
                ) { Text(stringResource(R.string.ui_separation_ok)) }
            },
        )

        SeparationStatus.Idle -> onClose()
    }
}

@Composable
private fun runningText(status: SeparationStatus.Running): List<String> {
    if (status.pausedForHeat) return listOf(stringResource(R.string.ui_separation_paused))
    val percent = stringResource(R.string.ui_separation_running, (status.progress * PERCENT).roundToInt())
    val minutes = status.remainingMs?.let { ceil(it / MS_PER_MIN).toInt().coerceAtLeast(1) } ?: return listOf(percent)
    return listOf(percent, stringResource(R.string.ui_separation_remaining, minutes))
}

@Composable
private fun failedText(status: SeparationStatus.Failed) = when (status.reason) {
    SeparationFailure.NO_SPACE -> stringResource(R.string.ui_separation_no_space)
    else -> stringResource(R.string.ui_separation_failed, status.title)
}

/** A palheta vermelha do ícone do app (desenhada em código); com falha, fica no tom de erro. */
@Composable
private fun Pick(failed: Boolean) {
    val color = if (failed) RiffColors.StampError else RiffColors.TourRed
    Canvas(Modifier.size(PICK)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h)
            cubicTo(w * 0.1f, h * 0.7f, 0f, h * 0.35f, w * 0.1f, h * 0.12f)
            cubicTo(w * 0.35f, 0f, w * 0.65f, 0f, w * 0.9f, h * 0.12f)
            cubicTo(w, h * 0.35f, w * 0.9f, h * 0.7f, w * 0.5f, h)
            close()
        }
        drawPath(path, color)
    }
}

private val RING = 34.dp
private val PICK = 18.dp
private const val PERCENT = 100
private const val MS_PER_MIN = 60_000.0
