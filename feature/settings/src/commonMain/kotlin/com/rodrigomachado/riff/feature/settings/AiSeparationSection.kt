package com.rodrigomachado.riff.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.SeparationUsage
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.input.menuKeys
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.settings.resources.Res
import com.rodrigomachado.riff.feature.settings.resources.settings_ai_ask_again
import com.rodrigomachado.riff.feature.settings.resources.settings_ai_ask_again_hint
import com.rodrigomachado.riff.feature.settings.resources.settings_ai_delete
import com.rodrigomachado.riff.feature.settings.resources.settings_ai_delete_confirm
import com.rodrigomachado.riff.feature.settings.resources.settings_ai_delete_no
import com.rodrigomachado.riff.feature.settings.resources.settings_ai_delete_yes
import com.rodrigomachado.riff.feature.settings.resources.settings_ai_only_charging
import com.rodrigomachado.riff.feature.settings.resources.settings_ai_only_charging_hint
import com.rodrigomachado.riff.feature.settings.resources.settings_ai_usage
import com.rodrigomachado.riff.feature.settings.resources.settings_ai_usage_none
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Guitarra separada por IA: o espaço usado, apagar as separações (com confirmação), perguntar de novo e separar a
 * fila só na tomada.
 */
@Composable
internal fun AiSeparationSection(
    usage: SeparationUsage,
    onDelete: () -> Unit,
    settings: Settings,
    onAskAlways: (Boolean) -> Unit,
    onOnlyCharging: (Boolean) -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    ActionRow(
        title = stringResource(Res.string.settings_ai_delete),
        value = usageText(usage),
        onClick = withMenuSound(MenuSound.NAVIGATE) { confirming = true },
    )
    SwitchRow(
        title = stringResource(Res.string.settings_ai_ask_again),
        hint = stringResource(Res.string.settings_ai_ask_again_hint),
        checked = settings.askSeparationAlways,
        onChange = onAskAlways,
        tag = SettingsTestTags.ASK_ALWAYS,
    )
    SwitchRow(
        title = stringResource(Res.string.settings_ai_only_charging),
        hint = stringResource(Res.string.settings_ai_only_charging_hint),
        checked = settings.separateOnlyCharging,
        onChange = onOnlyCharging,
        tag = SettingsTestTags.ONLY_CHARGING,
    )
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },

            // No PC as setas andam pelo diálogo, e o Esc o fecha (sem chegar à tela de trás).

            modifier = Modifier.menuKeys(onBack = { confirming = false }),
            text = { Text(stringResource(Res.string.settings_ai_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = withMenuSound(MenuSound.CONFIRM) {
                        confirming = false
                        onDelete()
                    },
                    modifier = Modifier.focusHighlight(CircleShape),
                ) { Text(stringResource(Res.string.settings_ai_delete_yes)) }
            },
            dismissButton = {
                TextButton(
                    onClick = withMenuSound(MenuSound.BACK) { confirming = false },
                    modifier = Modifier.focusHighlight(CircleShape),
                ) {
                    Text(stringResource(Res.string.settings_ai_delete_no))
                }
            },
        )
    }
}

@Composable
private fun usageText(usage: SeparationUsage): String = if (usage.songs == 0) {
    stringResource(Res.string.settings_ai_usage_none)
} else {
    pluralStringResource(
        Res.plurals.settings_ai_usage,
        usage.songs,
        usage.songs,
        formatFileSize(usage.bytes),
    )
}

/** Linha de ação como a da calibração: alinhada às outras, com o valor à direita ou uma explicação embaixo. */
@Composable
private fun ActionRow(title: String, onClick: () -> Unit, value: String? = null, hint: String? = null) {
    Row(
        Modifier.fillMaxWidth().focusHighlight().clickable(onClick = onClick).padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = RiffColors.Paper)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RiffColors.PaperDim) }
        }
        value?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = RiffColors.PaperDim) }
    }
}
