package com.rodrigomachado.riff.feature.settings

import android.text.format.Formatter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.SeparationUsage
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors

/** Guitarra separada por IA: o espaço usado, apagar as separações (com confirmação) e perguntar de novo. */
@Composable
internal fun AiSeparationSection(usage: SeparationUsage, onDelete: () -> Unit, onAskAgain: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Section(stringResource(R.string.settings_ai_section))
    Text(
        stringResource(
            R.string.settings_ai_usage,
            usage.songs,
            Formatter.formatShortFileSize(LocalContext.current, usage.bytes),
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = RiffColors.Paper,
    )
    TextButton(onClick = withMenuSound(MenuSound.NAVIGATE) { confirming = true }) {
        Text(stringResource(R.string.settings_ai_delete), color = RiffColors.Paper)
    }
    TextButton(onClick = withMenuSound(MenuSound.CONFIRM, onAskAgain)) {
        Text(stringResource(R.string.settings_ai_ask_again), color = RiffColors.Paper)
    }
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
