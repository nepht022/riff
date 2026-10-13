package com.rodrigomachado.riff.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

/** Originais | Versões e o interruptor "Só com guitarra separada". */
@Composable
internal fun FilterRow(state: SearchUiState, onShowVersions: (Boolean) -> Unit, onOnlyGuitar: (Boolean) -> Unit) {
    val onlyGuitar = withMenuSound(MenuSound.NAVIGATE, onOnlyGuitar)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Choice(stringResource(R.string.search_originals), selected = !state.versions) { onShowVersions(false) }
        Choice(stringResource(R.string.search_versions), selected = state.versions) { onShowVersions(true) }
    }
    Row(
        Modifier.fillMaxWidth().toggleable(state.onlySeparateGuitar, role = Role.Switch, onValueChange = onlyGuitar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.search_only_separate_guitar),
            color = RiffColors.Paper,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = state.onlySeparateGuitar,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RiffColors.Paper,
                checkedTrackColor = RiffColors.TourRed,
                uncheckedThumbColor = RiffColors.PaperDim,
                uncheckedTrackColor = RiffColors.StageBlack,
                uncheckedBorderColor = RiffColors.PaperDim,
            ),
        )
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    // Texto selecionável, não TextButton: sem recuo nem largura mínima, alinha à esquerda com o resto da tela.
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .selectable(selected, role = Role.Tab, onClick = withMenuSound(MenuSound.NAVIGATE, onClick)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) RiffColors.Paper else RiffColors.PaperDim,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textDecoration = if (selected) TextDecoration.Underline else TextDecoration.None,
        )
    }
}

/** A procura por guitarra separada: o progresso, ou "nenhum" com Procurar mais. */
@Composable
internal fun GuitarSearchStatus(search: GuitarSearch, nothingShown: Boolean, onSearchMore: () -> Unit) {
    when {
        search.running -> Text(
            stringResource(R.string.search_guitar_progress, search.checked, search.limit),
            style = MaterialTheme.typography.bodySmall,
            color = RiffColors.PaperDim,
        )

        nothingShown && !search.canSearchMore -> Text(
            stringResource(R.string.search_guitar_none_all, search.checked),
            color = RiffColors.Paper,
        )

        nothingShown -> Column {
            Text(stringResource(R.string.search_guitar_none, search.checked), color = RiffColors.Paper)
            TextButton(onClick = withMenuSound(MenuSound.NAVIGATE, onSearchMore)) {
                Text(stringResource(R.string.search_guitar_more), color = RiffColors.Paper)
            }
        }
    }
}
