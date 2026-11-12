package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.input.menuKeys
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.library.resources.Res
import com.rodrigomachado.riff.feature.library.resources.library_delete
import com.rodrigomachado.riff.feature.library.resources.library_delete_allow
import com.rodrigomachado.riff.feature.library.resources.library_delete_cancel
import com.rodrigomachado.riff.feature.library.resources.library_delete_failed
import com.rodrigomachado.riff.feature.library.resources.library_delete_message
import com.rodrigomachado.riff.feature.library.resources.library_delete_nested
import com.rodrigomachado.riff.feature.library.resources.library_delete_ok
import com.rodrigomachado.riff.feature.library.resources.library_delete_one
import com.rodrigomachado.riff.feature.library.resources.library_delete_permission
import com.rodrigomachado.riff.feature.library.resources.library_delete_title_many
import com.rodrigomachado.riff.feature.library.resources.library_delete_title_one
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** O que a tela faz na exclusão de mapas (o ViewModel liga cada um). */
class DeletionCallbacks(
    val onDelete: (String) -> Unit = {},
    val onStartSelecting: () -> Unit = {},
    val onToggle: (String) -> Unit = {},
    val onCancelSelecting: () -> Unit = {},
    /** Marcar (ou desmarcar) todos os mapas à mostra: os ids do filtro e do chip. */
    val onToggleAll: (visible: List<String>) -> Unit = {},
    val onDeleteSelected: () -> Unit = {},
    /** "Separar (n)": põe na fila de separação as selecionadas que precisam da IA. */
    val onSeparateSelected: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onFailureShown: () -> Unit = {},
)

/**
 * Os mesmos callbacks, avisando quando o X de um mapa pede para excluir ([onAsked]: o mapa e o vizinho que fica no
 * lugar dele em [ids], o seguinte ou, no fim, o anterior) e quando o diálogo fecha ([onClosed]: excluiu ou desistiu).
 */
internal fun DeletionCallbacks.refocusing(
    ids: List<String>,
    onAsked: (origin: String, neighbor: String?) -> Unit,
    onClosed: (confirmed: Boolean) -> Unit,
): DeletionCallbacks {
    val base = this
    return DeletionCallbacks(
        onDelete = { id ->
            val i = ids.indexOf(id)
            onAsked(id, ids.getOrNull(i + 1) ?: ids.getOrNull(i - 1))
            base.onDelete(id)
        },
        onStartSelecting = base.onStartSelecting,
        onToggle = base.onToggle,
        onCancelSelecting = base.onCancelSelecting,
        onToggleAll = base.onToggleAll,
        onDeleteSelected = base.onDeleteSelected,
        onSeparateSelected = base.onSeparateSelected,
        onConfirm = {
            onClosed(true)
            base.onConfirm()
        },
        onDismiss = {
            onClosed(false)
            base.onDismiss()
        },
        onFailureShown = base.onFailureShown,
    )
}

/**
 * O cartão. Fora do modo Selecionar, com o X vermelho no canto (sempre à vista; tocar no cartão joga). No modo
 * Selecionar ([selecting]), sem o X: a caixa de marcar à esquerda, e tocar no cartão marca. Sem [canDelete] (o mapa
 * solto na raiz, ou a escolha da música da sala) não tem nenhum dos dois. [focusMe]: o cartão recebe o foco (no PC,
 * depois do diálogo de excluir) e avisa por [onFocused].
 */
@Composable
@Suppress("LongParameterList") // o mapa, a seleção e o cartão
internal fun DeletableChart(
    title: String,
    chartId: String,
    selecting: Boolean,
    selected: Boolean,
    canDelete: Boolean,
    callbacks: DeletionCallbacks,
    focusMe: Boolean = false,
    onFocused: () -> Unit = {},
    card: @Composable (Modifier, onClickOverride: (() -> Unit)?) -> Unit,
) {
    if (!selecting) {
        ChartWithX(title, canDelete, focusMe, onFocused, onDelete = { callbacks.onDelete(chartId) }) { card(it, null) }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = selected,
            onCheckedChange = if (canDelete) ({ callbacks.onToggle(chartId) }) else null,
            enabled = canDelete,
            colors = CheckboxDefaults.colors(checkedColor = RiffColors.TourRed, uncheckedColor = RiffColors.Paper),
            modifier = Modifier.focusHighlight(),
        )
        Box(Modifier.weight(1f)) {
            card(Modifier, if (canDelete) ({ callbacks.onToggle(chartId) }) else ({}))
        }
    }
}

/**
 * O cartão com o X no canto de cima à direita (o cartão desce e encolhe um pouco para o X caber na linha da lista).
 * No PC o X fica fora do caminho de ↑/↓ (que andam de cartão em cartão): só → com o cartão focado o alcança, e o X
 * volta a ficar fora do caminho quando perde o foco.
 */
@Composable
@Suppress("LongParameterList") // o mapa, o foco e o cartão
private fun ChartWithX(
    title: String,
    canDelete: Boolean,
    focusMe: Boolean,
    onFocused: () -> Unit,
    onDelete: () -> Unit,
    card: @Composable (Modifier) -> Unit,
) {
    var armed by remember { mutableStateOf(false) }
    val x = remember { FocusRequester() }
    val cardFocus = remember { FocusRequester() }
    var xFocused by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (!armed) return@LaunchedEffect
        x.requestFocus()
        // Se o X não pegou o foco (o cartão saiu da tela no meio), ele volta a ficar fora do caminho de ↑/↓.
        withFrameNanos {}
        if (!xFocused) armed = false
    }
    LaunchedEffect(focusMe) {
        if (focusMe) {
            cardFocus.requestFocus()
            onFocused()
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .onKeyEvent { event ->
                // Também no celular com teclado ou controle: ↓ e Tab nunca param no X.
                val arm = canDelete && !armed && event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight
                if (arm) armed = true
                arm
            },
    ) {
        // Sem X (mapa solto na raiz, escolha da sala), o cartão ocupa a linha toda.
        val room = if (canDelete) Modifier.padding(top = X_OVERHANG, end = X_OVERHANG) else Modifier
        card(Modifier.focusRequester(cardFocus).then(room))
        if (canDelete) {
            DeleteX(
                title,
                Modifier
                    .align(Alignment.TopEnd)
                    .focusProperties { canFocus = armed }
                    .focusRequester(x),
                onFocus = { now ->
                    xFocused = now
                    if (!now) armed = false
                },
                onClick = onDelete,
            )
        }
    }
}

/**
 * X vermelho num círculo, no canto do cartão; a área de toque (44 dp) é maior que o círculo e fica quase toda fora do
 * cartão (só 16 dp entram nele: tocar no canto do cartão joga).
 */
@Composable
private fun DeleteX(title: String, modifier: Modifier, onFocus: (Boolean) -> Unit, onClick: () -> Unit) {
    val description = stringResource(Res.string.library_delete_one, title)
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(X_TOUCH)
            .onFocusChanged {
                if (focused != it.isFocused) onFocus(it.isFocused)
                focused = it.isFocused
            }
            .focusHighlight(CircleShape)
            .clickable(onClick = withMenuSound(MenuSound.NAVIGATE, onClick))
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(X_CIRCLE)
                .background(RiffColors.StageBlack, CircleShape)
                .padding(2.dp)
                .background(RiffColors.TourRed, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(11.dp)) {
                val stroke = 2.5.dp.toPx()
                drawLine(RiffColors.Paper, Offset.Zero, Offset(size.width, size.height), stroke, StrokeCap.Round)
                drawLine(RiffColors.Paper, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
            }
        }
    }
}

/** O quanto o X passa do canto do cartão (o cartão desce e encolhe isso para o X caber na linha). */
private val X_OVERHANG = 28.dp
private val X_TOUCH = 44.dp
private val X_CIRCLE = 30.dp

/** A confirmação, o pedido de permissão e o aviso de falha. */
@Composable
internal fun DeletionDialogs(state: DeletionState, callbacks: DeletionCallbacks, onAllow: () -> Unit) {
    state.ask?.let { ConfirmDeletion(it, callbacks) }
    if (state.needsPermission) {
        AlertDialog(
            onDismissRequest = callbacks.onDismiss,

            // No PC as setas andam pelo diálogo, e o Esc o fecha (sem chegar à tela de trás).

            modifier = Modifier.menuKeys(onBack = callbacks.onDismiss),
            text = { Text(stringResource(Res.string.library_delete_permission)) },
            confirmButton = {
                TextButton(
                    onClick = withMenuSound(MenuSound.NAVIGATE, onAllow),
                    modifier = Modifier.focusHighlight(CircleShape),
                ) {
                    Text(stringResource(Res.string.library_delete_allow))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = withMenuSound(MenuSound.BACK, callbacks.onDismiss),
                    modifier = Modifier.focusHighlight(CircleShape),
                ) {
                    Text(stringResource(Res.string.library_delete_cancel))
                }
            },
        )
    }
    if (state.failed > 0) {
        AlertDialog(
            onDismissRequest = callbacks.onFailureShown,

            // No PC as setas andam pelo diálogo, e o Esc o fecha (sem chegar à tela de trás).

            modifier = Modifier.menuKeys(onBack = callbacks.onFailureShown),
            text = { Text(pluralStringResource(Res.plurals.library_delete_failed, state.failed, state.failed)) },
            confirmButton = {
                TextButton(
                    onClick = withMenuSound(MenuSound.CONFIRM, callbacks.onFailureShown),
                    modifier = Modifier.focusHighlight(CircleShape),
                ) {
                    Text(stringResource(Res.string.library_delete_ok))
                }
            },
        )
    }
}

@Composable
private fun ConfirmDeletion(ask: DeleteAsk, callbacks: DeletionCallbacks) {
    AlertDialog(
        onDismissRequest = callbacks.onDismiss,

        // No PC as setas andam pelo diálogo, e o Esc o fecha (sem chegar à tela de trás).

        modifier = Modifier.menuKeys(onBack = callbacks.onDismiss),
        title = {
            Text(
                ask.title?.let { stringResource(Res.string.library_delete_title_one, it) }
                    ?: pluralStringResource(Res.plurals.library_delete_title_many, ask.ids.size, ask.ids.size),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(Res.string.library_delete_message))
                if (ask.alsoDeleted > 0) {
                    Text(pluralStringResource(Res.plurals.library_delete_nested, ask.alsoDeleted, ask.alsoDeleted))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = withMenuSound(MenuSound.CONFIRM, callbacks.onConfirm),
                modifier = Modifier.focusHighlight(CircleShape),
            ) {
                Text(stringResource(Res.string.library_delete), color = RiffColors.TourRed)
            }
        },
        dismissButton = {
            TextButton(
                onClick = withMenuSound(MenuSound.BACK, callbacks.onDismiss),
                modifier = Modifier.focusHighlight(CircleShape),
            ) {
                Text(stringResource(Res.string.library_delete_cancel))
            }
        },
    )
}
