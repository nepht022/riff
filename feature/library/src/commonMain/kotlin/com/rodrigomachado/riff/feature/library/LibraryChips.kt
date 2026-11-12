package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.library.resources.Res
import com.rodrigomachado.riff.feature.library.resources.library_chip_all
import com.rodrigomachado.riff.feature.library.resources.library_chip_record
import com.rodrigomachado.riff.feature.library.resources.library_separate_guitar
import org.jetbrains.compose.resources.stringResource

/** Os chips acima da lista: todas, só as de guitarra separada (a do mapa ou a da IA) e só as com recorde. */
enum class LibraryChip {
    ALL,
    SEPARATE_GUITAR,
    WITH_RECORD,
    ;

    fun accepts(chart: Chart, state: LibraryUiState): Boolean = when (this) {
        ALL -> true
        SEPARATE_GUITAR -> chart.separateGuitar || chart.id in state.aiSeparated
        WITH_RECORD -> chart.id in state.bests
    }
}

/** "Todas · N" (N: a biblioteca inteira), "Guitarra separada" e "Com recorde"; um só escolhido. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryChips(selected: LibraryChip, total: Int, onSelect: (LibraryChip) -> Unit) {
    // Em tela estreita (ou fonte grande), o que não cabe desce para a linha de baixo. Um grupo de escolha única: o
    // leitor de tela anuncia cada chip como um entre eles.
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        LibraryChip.entries.forEach { chip ->
            val label = when (chip) {
                LibraryChip.ALL -> stringResource(Res.string.library_chip_all, total)
                LibraryChip.SEPARATE_GUITAR -> stringResource(Res.string.library_separate_guitar)
                LibraryChip.WITH_RECORD -> stringResource(Res.string.library_chip_record)
            }
            Chip(label, chip == selected) { onSelect(chip) }
        }
    }
}

/** O chip: vermelho cheio quando escolhido, contorno de papel apagado quando não. */
@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = TOUCH)
            .focusHighlight(CircleShape)
            .background(if (selected) RiffColors.TourRed else Color.Transparent, CircleShape)
            .border(CHIP_BORDER, if (selected) RiffColors.TourRed else RiffColors.PaperDim, CircleShape)
            .selectable(selected, role = Role.Tab, onClick = withMenuSound(MenuSound.NAVIGATE, onClick))
            .padding(horizontal = CHIP_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = RiffColors.Paper, maxLines = 1)
    }
}

private val TOUCH = 44.dp
private val CHIP_BORDER = 1.dp
private val CHIP_PADDING = 12.dp
