package com.rodrigomachado.riff.feature.report

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.input.menuKeys
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.report.resources.Res
import com.rodrigomachado.riff.feature.report.resources.report_category_close
import com.rodrigomachado.riff.feature.report.resources.report_category_hint
import com.rodrigomachado.riff.feature.report.resources.report_category_title
import org.jetbrains.compose.resources.stringResource

/** Campo de categoria: mostra a escolhida (ou o convite) e abre a lista para tocar. */
@Composable
internal fun CategoryField(
    categories: List<ReportCategory>,
    selected: ReportCategory?,
    onChoose: (ReportCategory) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .border(1.dp, if (selected == null) RiffColors.PaperDim else RiffColors.Paper, FIELD_SHAPE)
            .focusHighlight(FIELD_SHAPE)
            .clickable(role = Role.DropdownList, onClick = withMenuSound(MenuSound.NAVIGATE) { open = true })
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            selected?.let { categoryName(it) } ?: stringResource(Res.string.report_category_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected == null) RiffColors.PaperDim else RiffColors.Paper,
            modifier = Modifier.weight(1f),
        )
        Text("▾", color = RiffColors.PaperDim)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            // No PC as setas andam pela lista, Enter escolhe e o Esc fecha só a lista (sem sair do Reportar).
            modifier = Modifier.menuKeys(onBack = { open = false }),
            title = { Text(stringResource(Res.string.report_category_title)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    for (category in categories) {
                        CategoryOption(category, category == selected) {
                            open = false
                            onChoose(category)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = withMenuSound(MenuSound.BACK) { open = false },
                    modifier = Modifier.focusHighlight(CircleShape),
                ) {
                    Text(stringResource(Res.string.report_category_close))
                }
            },
        )
    }
}

/** Uma opção da lista; "Selecionado" é lido pelo leitor de tela na marcada. */
@Composable
private fun CategoryOption(category: ReportCategory, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().focusHighlight().selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = withMenuSound(MenuSound.NAVIGATE, onClick),
        ).padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = RiffColors.TourRed),
        )
        Text(categoryName(category), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun categoryName(category: ReportCategory): String = stringResource(category.nameRes)

private val FIELD_SHAPE = RoundedCornerShape(4.dp)
