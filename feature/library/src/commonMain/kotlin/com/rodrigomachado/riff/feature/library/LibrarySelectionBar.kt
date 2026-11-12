package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterButtonDefaults
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.library.resources.Res
import com.rodrigomachado.riff.feature.library.resources.library_delete_cancel
import com.rodrigomachado.riff.feature.library.resources.library_delete_selected
import com.rodrigomachado.riff.feature.library.resources.library_select_all
import com.rodrigomachado.riff.feature.library.resources.library_select_none
import com.rodrigomachado.riff.feature.library.resources.library_selected_count
import com.rodrigomachado.riff.feature.library.resources.library_selected_none
import com.rodrigomachado.riff.feature.library.resources.library_separate_selected
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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
    /** Fonte grande ou tela baixa: tudo numa linha só, que rola de lado (a lista continua com espaço). */
    compact: Boolean = false,
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
            if (compact) {
                CompactBar(selected, separable, allSelected, callbacks) { callbacks.onToggleAll(visible) }
                return@Column
            }
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
                CancelButton(callbacks)
            }
        }
    }
}

/**
 * A barra numa linha só, que rola de lado: Excluir (n), Separar (n), Cancelar e Marcar todas. Sem a contagem à parte
 * (os botões já dizem quantas), para o Excluir caber à vista mesmo com a fonte grande.
 */
@Composable
private fun CompactBar(
    selected: Int,
    separable: Int,
    allSelected: Boolean,
    callbacks: DeletionCallbacks,
    onToggleAll: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterButton(
            stringResource(Res.string.library_delete_selected, selected),
            onClick = callbacks.onDeleteSelected,
            enabled = selected > 0,
            sound = MenuSound.NAVIGATE,
            textStyle = PosterButtonDefaults.pairTextStyle(),
            horizontalPadding = PosterButtonDefaults.PairPadding,
        )
        PosterButton(
            stringResource(Res.string.library_separate_selected, separable),
            onClick = callbacks.onSeparateSelected,
            enabled = separable > 0,
            sound = MenuSound.CONFIRM,
            textStyle = PosterButtonDefaults.pairTextStyle(),
            horizontalPadding = PosterButtonDefaults.PairPadding,
            containerColor = RiffColors.Paper,
            contentColor = RiffColors.Ink,
        )
        CancelButton(callbacks)
        SelectAll(allSelected, onToggleAll)
    }
}

@Composable
private fun CancelButton(callbacks: DeletionCallbacks) {
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

/** "N marcadas" (ou "Nenhuma marcada"); o leitor de tela avisa quando muda. */
@Composable
private fun CountText(selected: Int) {
    Text(
        // No português o zero vai no singular ("0 marcada"): sem nada marcado, "Nenhuma marcada".
        if (selected == 0) {
            stringResource(Res.string.library_selected_none)
        } else {
            pluralStringResource(Res.plurals.library_selected_count, selected, selected)
        },
        style = MaterialTheme.typography.titleMedium,
        color = RiffColors.Paper,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** "N marcadas" e, na ponta, Marcar todas (com a fonte grande, desce para a linha de baixo sem espremer a contagem). */
@Composable
private fun SelectionCount(selected: Int, allSelected: Boolean, onToggleAll: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        CountText(selected)
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

/** O fundo da barra de baixo (mais escuro que o palco) e a borda vermelha de cima. */
private val BarBackground = Color(0xFF0A0706)
private val BarBorder = 2.dp
