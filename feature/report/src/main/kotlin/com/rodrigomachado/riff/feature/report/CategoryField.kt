package com.rodrigomachado.riff.feature.report

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

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
            .clickable(role = Role.DropdownList, onClick = withMenuSound(MenuSound.NAVIGATE) { open = true })
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            selected?.let { categoryName(it) } ?: stringResource(R.string.report_category_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected == null) RiffColors.PaperDim else RiffColors.Paper,
            modifier = Modifier.weight(1f),
        )
        Text("▾", color = RiffColors.PaperDim)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.report_category_title)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    for (category in categories) {
                        Row(
                            Modifier.fillMaxWidth().clickable(
                                role = Role.RadioButton,
                                onClick = withMenuSound(MenuSound.NAVIGATE) {
                                    open = false
                                    onChoose(category)
                                },
                            ).padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            RadioButton(
                                selected = category == selected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = RiffColors.TourRed),
                            )
                            Text(categoryName(category), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = withMenuSound(MenuSound.BACK) { open = false }) {
                    Text(stringResource(R.string.report_category_close))
                }
            },
        )
    }
}

@Composable
private fun categoryName(category: ReportCategory): String = stringResource(category.nameRes)

private val FIELD_SHAPE = RoundedCornerShape(4.dp)
