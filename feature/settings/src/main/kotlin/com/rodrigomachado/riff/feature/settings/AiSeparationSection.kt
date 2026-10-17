package com.rodrigomachado.riff.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.SeparationUsage
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

/** Guitarra separada por IA: o espaço usado, apagar as separações (com confirmação) e perguntar de novo. */
@Composable
internal fun AiSeparationSection(
    usage: SeparationUsage,
    onDelete: () -> Unit,
    askAlways: Boolean,
    onAskAlways: (Boolean) -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    ActionRow(
        title = stringResource(R.string.settings_ai_delete),
        value = usageText(usage),
        onClick = withMenuSound(MenuSound.NAVIGATE) { confirming = true },
    )
    SwitchRow(
        title = stringResource(R.string.settings_ai_ask_again),
        hint = stringResource(R.string.settings_ai_ask_again_hint),
        checked = askAlways,
        onChange = onAskAlways,
        tag = SettingsTestTags.ASK_ALWAYS,
    )
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            text = { Text(stringResource(R.string.settings_ai_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = withMenuSound(MenuSound.CONFIRM) {
                        confirming = false
                        onDelete()
                    },
                ) { Text(stringResource(R.string.settings_ai_delete_yes)) }
            },
            dismissButton = {
                TextButton(onClick = withMenuSound(MenuSound.BACK) { confirming = false }) {
                    Text(stringResource(R.string.settings_ai_delete_no))
                }
            },
        )
    }
}

@Composable
private fun usageText(usage: SeparationUsage): String = if (usage.songs == 0) {
    stringResource(R.string.settings_ai_usage_none)
} else {
    pluralStringResource(
        R.plurals.settings_ai_usage,
        usage.songs,
        usage.songs,
        Formatter.formatShortFileSize(LocalContext.current, usage.bytes),
    )
}

/** Linha de ação como a da calibração: alinhada às outras, com o valor à direita ou uma explicação embaixo. */
@Composable
private fun ActionRow(title: String, onClick: () -> Unit, value: String? = null, hint: String? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = RiffColors.Paper)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RiffColors.PaperDim) }
        }
        value?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = RiffColors.PaperDim) }
    }
}
