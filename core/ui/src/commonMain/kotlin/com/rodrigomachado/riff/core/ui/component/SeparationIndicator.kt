package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.QueuedSeparation
import com.rodrigomachado.riff.core.model.SeparationFailure
import com.rodrigomachado.riff.core.model.SeparationStatus
import com.rodrigomachado.riff.core.ui.resources.Res
import com.rodrigomachado.riff.core.ui.resources.ui_separation_cancel
import com.rodrigomachado.riff.core.ui.resources.ui_separation_cancel_all
import com.rodrigomachado.riff.core.ui.resources.ui_separation_cancel_this
import com.rodrigomachado.riff.core.ui.resources.ui_separation_close
import com.rodrigomachado.riff.core.ui.resources.ui_separation_failed
import com.rodrigomachado.riff.core.ui.resources.ui_separation_no_space
import com.rodrigomachado.riff.core.ui.resources.ui_separation_ok
import com.rodrigomachado.riff.core.ui.resources.ui_separation_open
import com.rodrigomachado.riff.core.ui.resources.ui_separation_open_count
import com.rodrigomachado.riff.core.ui.resources.ui_separation_paused
import com.rodrigomachado.riff.core.ui.resources.ui_separation_queue
import com.rodrigomachado.riff.core.ui.resources.ui_separation_remaining
import com.rodrigomachado.riff.core.ui.resources.ui_separation_remove
import com.rodrigomachado.riff.core.ui.resources.ui_separation_running
import com.rodrigomachado.riff.core.ui.resources.ui_separation_starting
import com.rodrigomachado.riff.core.ui.resources.ui_separation_waiting_charger
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil
import kotlin.math.roundToInt

/** A separação da guitarra em segundo plano, fornecida pelo app (as telas só põem o ícone). */
interface SeparationControls {
    val status: StateFlow<SeparationStatus>

    /** As que esperam a vez, sem a atual. */
    val queue: StateFlow<List<QueuedSeparation>>

    val waitingForCharger: StateFlow<Boolean>

    /** Cancela [chartId] se ainda for a atual; a fila segue. */
    fun cancel(chartId: String)

    fun remove(chartId: String)

    fun cancelAll()

    fun dismissFailure()
}

/** A fila no painel do ícone: as que esperam, se esperam o carregador e as ações de tirar. */
data class SeparationQueueView(
    val items: List<QueuedSeparation> = emptyList(),
    val waitingForCharger: Boolean = false,
    val onRemove: (String) -> Unit = {},
    val onCancelAll: () -> Unit = {},
)

val LocalSeparation = staticCompositionLocalOf<SeparationControls?> { null }

object SeparationIndicatorTags {
    const val ICON = "separation_icon"
}

/** O ícone da separação no topo da tela; some quando não há separação. */
@Composable
fun SeparationIndicator(modifier: Modifier = Modifier) {
    val controls = LocalSeparation.current ?: return
    val status by controls.status.collectAsState()
    val queued by controls.queue.collectAsState()
    val waitingForCharger by controls.waitingForCharger.collectAsState()
    SeparationIndicatorContent(
        status,
        { (status as? SeparationStatus.Running)?.let { controls.cancel(it.chartId) } },
        controls::dismissFailure,
        modifier,
        SeparationQueueView(queued, waitingForCharger, controls::remove, controls::cancelAll),
    )
}

@Composable
fun SeparationIndicatorContent(
    status: SeparationStatus,
    onCancel: () -> Unit,
    onDismissFailure: () -> Unit,
    modifier: Modifier = Modifier,
    queue: SeparationQueueView = SeparationQueueView(),
) {
    if (status == SeparationStatus.Idle && queue.items.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val total = queue.items.size + if (status is SeparationStatus.Running) 1 else 0
    val description = if (total > 1) {
        stringResource(Res.string.ui_separation_open_count, total)
    } else {
        stringResource(Res.string.ui_separation_open)
    }
    // Com fila, a falha da última já foi avisada pela notificação: o ícone segue a fila.
    val failure = (status as? SeparationStatus.Failed)?.takeIf { queue.items.isEmpty() }
    // O número fica fora do botão, que recorta o conteúdo no círculo do toque.
    Box(modifier) {
        IconButton(
            onClick = withMenuSound(MenuSound.NAVIGATE) { open = true },
            modifier = Modifier.testTag(SeparationIndicatorTags.ICON).semantics { contentDescription = description },
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
                Pick(failed = failure != null)
            }
        }
        if (total > 1) Count(total, Modifier.align(Alignment.TopEnd))
    }
    if (open) {
        val close = { open = false }
        if (failure != null) {
            FailurePanel(failure, onDismissFailure, close)
        } else {
            QueuePanel(status, onCancel, queue, close)
        }
    }
}

/** Quantas faltam (a atual + a fila), no canto do ícone. */
@Composable
private fun Count(total: Int, modifier: Modifier) {
    Box(
        // O número já está na descrição do ícone.
        modifier.size(COUNT).background(RiffColors.TourRed, CircleShape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            total.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = COUNT_TEXT, lineHeight = COUNT_TEXT),
            color = RiffColors.Paper,
        )
    }
}

@Composable
private fun FailurePanel(status: SeparationStatus.Failed, onDismissFailure: () -> Unit, onClose: () -> Unit) =
    AlertDialog(
        onDismissRequest = onClose,
        text = { Text(failedText(status)) },
        confirmButton = {
            TextButton(
                onClick = withMenuSound(MenuSound.CONFIRM) {
                    onClose()
                    onDismissFailure()
                },
            ) { Text(stringResource(Res.string.ui_separation_ok)) }
        },
    )

/** A atual (com o progresso) ou a espera, e embaixo as da fila com o X de cada uma. */
@Composable
private fun QueuePanel(
    status: SeparationStatus,
    onCancel: () -> Unit,
    queue: SeparationQueueView,
    onClose: () -> Unit,
) {
    val running = status as? SeparationStatus.Running
    val hasQueue = queue.items.isNotEmpty()
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            val waiting = if (queue.waitingForCharger) {
                Res.string.ui_separation_waiting_charger
            } else {
                Res.string.ui_separation_starting
            }
            Text(running?.title ?: stringResource(waiting))
        },
        text = {
            // Uma fila longa rola dentro do diálogo: o X de cada música continua alcançável.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                running?.let { runningText(it).forEach { line -> Text(line) } }
                if (hasQueue) QueueList(queue)
            }
        },
        confirmButton = {
            if (running != null) {
                TextButton(
                    onClick = withMenuSound(MenuSound.BACK) {
                        onClose()
                        onCancel()
                    },
                ) {
                    Text(
                        stringResource(
                            if (hasQueue) Res.string.ui_separation_cancel_this else Res.string.ui_separation_cancel,
                        ),
                    )
                }
            } else {
                TextButton(onClick = withMenuSound(MenuSound.BACK, onClose)) {
                    Text(stringResource(Res.string.ui_separation_close))
                }
            }
        },
        dismissButton = {
            if (hasQueue) {
                TextButton(
                    onClick = withMenuSound(MenuSound.BACK) {
                        onClose()
                        queue.onCancelAll()
                    },
                ) { Text(stringResource(Res.string.ui_separation_cancel_all)) }
            }
        },
    )
}

@Composable
private fun QueueList(queue: SeparationQueueView) {
    Text(
        stringResource(Res.string.ui_separation_queue, queue.items.size),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = Spacing.md),
    )
    queue.items.forEach { song ->
        val remove = stringResource(Res.string.ui_separation_remove, song.title)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(song.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(
                onClick = withMenuSound(MenuSound.BACK) { queue.onRemove(song.chartId) },
                // Só a descrição: o "✕" desenhado não é lido.
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = remove
                    role = Role.Button
                },
            ) { Text("✕", color = RiffColors.PaperDim) }
        }
    }
}

@Composable
private fun runningText(status: SeparationStatus.Running): List<String> {
    if (status.pausedForHeat) return listOf(stringResource(Res.string.ui_separation_paused))
    val percent = stringResource(Res.string.ui_separation_running, (status.progress * PERCENT).roundToInt())
    val minutes = status.remainingMs?.let { ceil(it / MS_PER_MIN).toInt().coerceAtLeast(1) } ?: return listOf(percent)
    return listOf(percent, stringResource(Res.string.ui_separation_remaining, minutes))
}

@Composable
private fun failedText(status: SeparationStatus.Failed) = when (status.reason) {
    SeparationFailure.NO_SPACE -> stringResource(Res.string.ui_separation_no_space)
    else -> stringResource(Res.string.ui_separation_failed, status.title)
}

/** A palheta vermelha do ícone do app (desenhada em código); com falha, fica no tom de erro. */
@Composable
private fun Pick(failed: Boolean) {
    val color = if (failed) RiffColors.StampError else RiffColors.TourRed
    // Larga em cima e fina na ponta: centrada pela caixa ela parece subida; desce até o centro de massa cair no meio.
    Canvas(Modifier.offset(y = PICK_NUDGE).size(PICK)) {
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
private val COUNT = 18.dp
private val COUNT_TEXT = 11.sp
private val PICK = 18.dp
private val PICK_NUDGE = 1.5.dp
private const val PERCENT = 100
private const val MS_PER_MIN = 60_000.0
