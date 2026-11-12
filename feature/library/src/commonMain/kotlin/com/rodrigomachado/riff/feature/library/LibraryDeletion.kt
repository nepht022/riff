package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterButtonDefaults
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.input.menuKeys
import com.rodrigomachado.riff.core.ui.input.menuKeysEnabled
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
import com.rodrigomachado.riff.feature.library.resources.library_selected_count
import com.rodrigomachado.riff.feature.library.resources.library_selected_none
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
 * A barra fixa embaixo da tela durante a seleção: em cima, "N marcadas" e Marcar todas (os [visible]: só os mapas à
 * mostra); embaixo, Separar (n) (põe na fila da IA as marcadas que precisam), Excluir (n) e um Cancelar pequeno.
 */
@Composable
@Suppress("LongParameterList") // as contagens, o "todos" e o que cada botão faz
internal fun SelectionBar(
    selected: Int,
    separable: Int,
    visible: List<String>,
    allSelected: Boolean,
    callbacks: DeletionCallbacks,
    modifier: Modifier = Modifier,
    /** A largura do conteúdo (no PC, a da coluna da lista); o fundo vai de ponta a ponta. */
    contentWidth: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(BarBackground)
            .drawBehind { drawRect(RiffColors.TourRed, size = Size(size.width, BarBorder.toPx())) }
            .padding(top = BarBorder),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            contentWidth.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SelectionCount(selected, allSelected) { callbacks.onToggleAll(visible) }
            BarActions {
                PosterButton(
                    stringResource(Res.string.library_separate_selected, separable),
                    onClick = callbacks.onSeparateSelected,
                    enabled = separable > 0,
                    sound = MenuSound.CONFIRM,
                    textStyle = PosterButtonDefaults.pairTextStyle(),
                    horizontalPadding = PosterButtonDefaults.PairPadding,
                    containerColor = RiffColors.Paper,
                    contentColor = RiffColors.Ink,
                    fillWidth = true,
                )
                PosterButton(
                    stringResource(Res.string.library_delete_selected, selected),
                    onClick = callbacks.onDeleteSelected,
                    enabled = selected > 0,
                    sound = MenuSound.NAVIGATE,
                    textStyle = PosterButtonDefaults.pairTextStyle(),
                    horizontalPadding = PosterButtonDefaults.PairPadding,
                    fillWidth = true,
                )
                TextButton(
                    onClick = withMenuSound(MenuSound.BACK, callbacks.onCancelSelecting),
                    modifier = Modifier.focusHighlight(CircleShape),
                ) {
                    Text(
                        stringResource(Res.string.library_delete_cancel),
                        color = RiffColors.Paper,
                        textDecoration = TextDecoration.Underline,
                    )
                }
            }
        }
    }
}

/** "N marcadas" e, na ponta, Marcar todas (com a fonte grande, desce para a linha de baixo sem espremer a contagem). */
@Composable
private fun SelectionCount(selected: Int, allSelected: Boolean, onToggleAll: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // No português o zero vai no singular ("0 marcada"): sem nada marcado, "Nenhuma marcada".
            if (selected == 0) {
                stringResource(Res.string.library_selected_none)
            } else {
                pluralStringResource(Res.plurals.library_selected_count, selected, selected)
            },
            style = MaterialTheme.typography.titleMedium,
            color = RiffColors.Paper,
        )
        SelectAll(allSelected, onToggleAll)
    }
}

/**
 * Separar e Excluir lado a lado e da mesma largura, com o Cancelar no fim da linha. Se não couberem (fonte grande),
 * o Cancelar desce para a linha de baixo; se nem assim, cada botão ganha a sua linha (o texto nunca é cortado).
 */
@Composable
private fun BarActions(content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
        val (separate, delete, cancel) = measurables
        val gap = Spacing.sm.roundToPx()
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val cancelPlaced = cancel.measure(loose)
        val need = maxOf(
            separate.maxIntrinsicWidth(constraints.maxHeight),
            delete.maxIntrinsicWidth(constraints.maxHeight),
        )
        val inRow = (width - cancelPlaced.width - 2 * gap) / 2
        val paired = (width - gap) / 2
        if (inRow >= need) {
            val fixed = loose.copy(minWidth = inRow, maxWidth = inRow)
            val buttons = listOf(separate, delete).map { it.measure(fixed) }
            val height = maxOf(buttons.maxOf { it.height }, cancelPlaced.height)
            layout(width, height) {
                buttons.forEachIndexed { i, button ->
                    button.place(i * (inRow + gap), (height - button.height) / 2)
                }
                cancelPlaced.place(width - cancelPlaced.width, (height - cancelPlaced.height) / 2)
            }
        } else {
            val each = if (paired >= need) paired else width
            val fixed = loose.copy(minWidth = each, maxWidth = each)
            val buttons = listOf(separate, delete).map { it.measure(fixed) }
            val side = each != width
            val firstRow = if (side) buttons.maxOf { it.height } else buttons[0].height
            val buttonsHeight = if (side) firstRow else firstRow + gap + buttons[1].height
            val height = buttonsHeight + gap + cancelPlaced.height
            layout(width, height) {
                if (side) {
                    buttons[0].place(0, 0)
                    buttons[1].place(each + gap, 0)
                } else {
                    buttons[0].place(0, 0)
                    buttons[1].place(0, firstRow + gap)
                }
                cancelPlaced.place(width - cancelPlaced.width, buttonsHeight + gap)
            }
        }
    }
}

/** Duas caixas empilhadas com o visto e "Marcar todas"; a caixa fica vermelha quando todas já estão marcadas. */
@Composable
private fun SelectAll(allSelected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .focusHighlight()
            .clickable(role = Role.Button, onClick = withMenuSound(MenuSound.NAVIGATE, onClick))
            .padding(horizontal = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectAllIcon(allSelected)
        Text(
            stringResource(if (allSelected) Res.string.library_select_none else Res.string.library_select_all),
            style = MaterialTheme.typography.titleSmall,
            color = RiffColors.Paper,
        )
    }
}

@Composable
private fun SelectAllIcon(allSelected: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        val box = size.width * 0.74f
        val gap = size.width - box
        val corner = CornerRadius(3.dp.toPx())
        // A caixa de trás, só o canto à mostra.
        drawRoundRect(RiffColors.Paper, Offset(gap, 0f), Size(box, box), corner, style = Stroke(stroke))
        val front = Offset(0f, gap)
        drawRoundRect(BarBackground, front, Size(box, box), corner)
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

/**
 * O cartão. Fora do modo Selecionar, com o X vermelho no canto (sempre à vista; tocar no cartão joga). No modo
 * Selecionar ([selecting]), sem o X: a caixa de marcar à esquerda, e tocar no cartão marca. O mapa protegido
 * ([canDelete] falso: solto na raiz ou em separação) não tem nenhum dos dois.
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
    card: @Composable (Modifier, onClickOverride: (() -> Unit)?) -> Unit,
) {
    if (!selecting) {
        ChartWithX(title, canDelete, onDelete = { callbacks.onDelete(chartId) }) { card(it, null) }
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
private fun ChartWithX(title: String, canDelete: Boolean, onDelete: () -> Unit, card: @Composable (Modifier) -> Unit) {
    var armed by remember { mutableStateOf(false) }
    val x = remember { FocusRequester() }
    LaunchedEffect(armed) { if (armed) x.requestFocus() }
    Box(
        Modifier.fillMaxWidth().onKeyEvent { event ->
            val arm = menuKeysEnabled && canDelete && !armed && event.type == KeyEventType.KeyDown &&
                event.key == Key.DirectionRight
            if (arm) armed = true
            arm
        },
    ) {
        card(Modifier.padding(top = X_OVERHANG, end = X_OVERHANG))
        if (canDelete) {
            DeleteX(
                title,
                Modifier
                    .align(Alignment.TopEnd)
                    .focusProperties { canFocus = armed || !menuKeysEnabled }
                    .focusRequester(x),
                onLeave = { armed = false },
                onClick = onDelete,
            )
        }
    }
}

/** X vermelho num círculo, no canto do cartão; a área de toque (48 dp) é maior que o círculo. */
@Composable
private fun DeleteX(title: String, modifier: Modifier, onLeave: () -> Unit, onClick: () -> Unit) {
    val description = stringResource(Res.string.library_delete_one, title)
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(X_TOUCH)
            .onFocusChanged {
                if (focused && !it.isFocused) onLeave()
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
private val X_OVERHANG = 14.dp
private val X_TOUCH = 48.dp
private val X_CIRCLE = 30.dp

/** O fundo da barra de baixo (mais escuro que o palco) e a borda vermelha de cima. */
private val BarBackground = Color(0xFF0A0706)
private val BarBorder = 2.dp

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
