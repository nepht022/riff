package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.BackRow
import com.rodrigomachado.riff.core.ui.component.SeparationIndicator
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.input.menuKeys
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.library.resources.Res
import com.rodrigomachado.riff.feature.library.resources.library_change_folder
import com.rodrigomachado.riff.feature.library.resources.library_more_charts
import com.rodrigomachado.riff.feature.library.resources.library_more_options
import com.rodrigomachado.riff.feature.library.resources.library_rescan
import com.rodrigomachado.riff.feature.library.resources.library_select
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** O que o menu ⋮ do topo faz; [onRescan] nulo (sem pasta) e [onSelect] nulo (sem mapas) tiram o item. */
internal class LibraryMenuActions(
    val onRescan: (() -> Unit)?,
    val onPickFolder: () -> Unit,
    val onSelect: (() -> Unit)?,
)

/** O topo: Voltar (seta), a palheta da separação no meio e o ⋮ com Reimportar, Trocar pasta e Selecionar. */
@Composable
internal fun LibraryTop(onBack: () -> Unit, menu: LibraryMenuActions) {
    Box(Modifier.fillMaxWidth()) {
        BackRow(onBack = onBack, modifier = Modifier.align(Alignment.CenterStart))
        SeparationIndicator(Modifier.align(Alignment.Center))
        LibraryMenu(menu, Modifier.align(Alignment.CenterEnd))
    }
}

/**
 * O ⋮ e o menu dele. No PC o menu é uma tela de menus à parte (setas e Enter nos itens, Esc o fecha sem sair da
 * Biblioteca); escolher um item o fecha.
 */
@Composable
private fun LibraryMenu(actions: LibraryMenuActions, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val close = { open = false }
    Box(modifier) {
        val description = stringResource(Res.string.library_more_options)
        IconButton(
            onClick = withMenuSound(MenuSound.NAVIGATE) { open = true },
            modifier = Modifier.semantics { contentDescription = description }.focusHighlight(CircleShape),
        ) {
            Canvas(Modifier.size(MENU_DOTS)) {
                val radius = size.width * DOT_RADIUS
                listOf(0.2f, 0.5f, 0.8f).forEach { y ->
                    drawCircle(RiffColors.Paper, radius, Offset(size.width / 2, size.height * y))
                }
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = close,
            modifier = Modifier.menuKeys(onBack = close),
            containerColor = RiffColors.Paper,
        ) {
            // Escolher um item fecha o menu antes de agir.
            actions.onRescan?.let { MenuItem(Res.string.library_rescan, close then it) }
            MenuItem(Res.string.library_change_folder, close then actions.onPickFolder)
            actions.onSelect?.let { MenuItem(Res.string.library_select, close then it) }
        }
    }
}

/** Faz `this` e depois [next]. */
private infix fun (() -> Unit).then(next: () -> Unit): () -> Unit = {
    this()
    next()
}

@Composable
private fun MenuItem(label: StringResource, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label), style = MaterialTheme.typography.titleMedium, color = RiffColors.Ink) },
        onClick = withMenuSound(MenuSound.NAVIGATE, onClick),
        modifier = Modifier.focusHighlight(MaterialTheme.shapes.small),
    )
}

/** "+ Mapas": a pesquisa no Encore, ao lado do título. */
@Composable
internal fun MoreChartsButton(onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = TOUCH)
            .focusHighlight(MaterialTheme.shapes.small)
            .background(RiffColors.TourRed, MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = withMenuSound(MenuSound.NAVIGATE, onClick))
            .padding(horizontal = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.library_more_charts),
            style = MaterialTheme.typography.titleMedium,
            color = RiffColors.Paper,
        )
    }
}

private val TOUCH = 44.dp
private val MENU_DOTS = 24.dp
private const val DOT_RADIUS = 0.09f
