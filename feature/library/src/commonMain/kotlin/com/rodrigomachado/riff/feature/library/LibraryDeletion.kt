package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.PosterButton
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
import com.rodrigomachado.riff.feature.library.resources.library_delete_selected
import com.rodrigomachado.riff.feature.library.resources.library_delete_title_many
import com.rodrigomachado.riff.feature.library.resources.library_delete_title_one
import com.rodrigomachado.riff.feature.library.resources.library_select_all
import com.rodrigomachado.riff.feature.library.resources.library_select_none
import com.rodrigomachado.riff.feature.library.resources.library_separate_selected
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
 * Acima da lista, durante a seleção: Excluir (n), Cancelar e, na ponta, marcar todos (os [visible]: só os mapas à
 * mostra); embaixo, Separar (n) põe na fila da IA as selecionadas que precisam.
 */
@Composable
internal fun SelectionActions(
    selected: Int,
    separable: Int,
    visible: List<String>,
    allSelected: Boolean,
    callbacks: DeletionCallbacks,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        DeleteRow(selected, allSelected, callbacks) { callbacks.onToggleAll(visible) }
        PosterButton(
            stringResource(Res.string.library_separate_selected, separable),
            onClick = callbacks.onSeparateSelected,
            enabled = separable > 0,
            sound = MenuSound.CONFIRM,
        )
    }
}

@Composable
private fun DeleteRow(selected: Int, allSelected: Boolean, callbacks: DeletionCallbacks, onToggleAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterButton(
            stringResource(Res.string.library_delete_selected, selected),
            onClick = callbacks.onDeleteSelected,
            enabled = selected > 0,
            sound = MenuSound.NAVIGATE,
        )
        TextButton(
            onClick = withMenuSound(MenuSound.BACK, callbacks.onCancelSelecting),
            modifier = Modifier.focusHighlight(CircleShape),
        ) {
            Text(stringResource(Res.string.library_delete_cancel), color = RiffColors.Paper)
        }
        Spacer(Modifier.weight(1f))
        SelectAll(allSelected, onToggleAll)
    }
}

/** Duas caixas empilhadas com o visto; cheia de vermelho quando todos já estão marcados. */
@Composable
private fun SelectAll(allSelected: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (allSelected) Res.string.library_select_none else Res.string.library_select_all)
    Box(
        Modifier
            .size(48.dp)
            .focusHighlight()
            .clickable(onClick = withMenuSound(MenuSound.NAVIGATE, onClick))
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(26.dp)) {
            val stroke = 2.dp.toPx()
            val box = size.width * 0.74f
            val gap = size.width - box
            val corner = CornerRadius(3.dp.toPx())
            // A caixa de trás, só o canto à mostra.
            drawRoundRect(RiffColors.Paper, Offset(gap, 0f), Size(box, box), corner, style = Stroke(stroke))
            val front = Offset(0f, gap)
            drawRoundRect(RiffColors.StageBlack, front, Size(box, box), corner)
            if (allSelected) {
                drawRoundRect(RiffColors.TourRed, front, Size(box, box), corner)
            } else {
                drawRoundRect(RiffColors.Paper, front, Size(box, box), corner, style = Stroke(stroke))
            }
            val check = Path().apply {
                moveTo(box * 0.22f, gap + box * 0.52f)
                lineTo(box * 0.42f, gap + box * 0.72f)
                lineTo(box * 0.78f, gap + box * 0.30f)
            }
            drawPath(check, RiffColors.Paper, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/**
 * O cartão; no modo Selecionar ([selecting]), com a caixa de marcar e o X vermelho no canto (fora dele, nada de
 * apagar: tocar no cartão só joga). O mapa solto na raiz ([canDelete] falso) não tem nenhum dos dois.
 */
@Composable
internal fun DeletableChart(
    title: String,
    chartId: String,
    selecting: Boolean,
    selected: Boolean,
    canDelete: Boolean,
    callbacks: DeletionCallbacks,
    card: @Composable (Modifier, onClickOverride: (() -> Unit)?) -> Unit,
) {
    if (!selecting) {
        card(Modifier, null)
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
            if (canDelete) DeleteX(title, Modifier.align(Alignment.TopEnd)) { callbacks.onDelete(chartId) }
        }
    }
}

/** X vermelho num círculo, no canto do cartão. */
@Composable
private fun DeleteX(title: String, modifier: Modifier, onClick: () -> Unit) {
    val description = stringResource(Res.string.library_delete_one, title)
    Box(
        modifier
            .offset(x = 6.dp, y = (-6).dp)
            .size(32.dp)
            .background(RiffColors.TourRed, CircleShape)
            .focusHighlight(CircleShape)
            .clickable(onClick = withMenuSound(MenuSound.NAVIGATE, onClick))
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) {
            val stroke = 2.5.dp.toPx()
            drawLine(RiffColors.Paper, Offset.Zero, Offset(size.width, size.height), stroke, StrokeCap.Round)
            drawLine(RiffColors.Paper, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
        }
    }
}

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
