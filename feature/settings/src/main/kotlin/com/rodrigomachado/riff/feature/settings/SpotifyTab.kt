package com.rodrigomachado.riff.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

/** Por que a fila não está ligada: o usuário recusou a autorização, ou o Spotify pediu para autorizar de novo. */
enum class QueueMessage { REFUSED, LOST }

data class QueueUiState(val message: QueueMessage?, val canWriteToLibrary: Boolean)

/** O que a aba Spotify pede ao ViewModel. */
sealed interface QueueAction {
    data class Prefetch(val on: Boolean) : QueueAction

    data class DeleteUnplayed(val on: Boolean) : QueueAction

    data class UseMobileData(val on: Boolean) : QueueAction

    data class ReplaceSuggested(val replace: Boolean) : QueueAction
}

/** Mapas da fila do Spotify: a opção principal, as três que dependem dela e os avisos. */
@Composable
internal fun SpotifyTab(settings: Settings, queue: QueueUiState, onQueue: (QueueAction) -> Unit) {
    val on = settings.queuePrefetch
    SwitchRow(
        title = stringResource(R.string.settings_queue_prefetch),
        hint = stringResource(R.string.settings_queue_prefetch_hint),
        checked = on,
        onChange = { onQueue(QueueAction.Prefetch(it)) },
        tag = SettingsTestTags.QUEUE_PREFETCH,
    )
    queue.message?.let { message ->
        Notice(
            stringResource(
                if (message == QueueMessage.REFUSED) R.string.settings_queue_refused else R.string.settings_queue_lost,
            ),
        )
    }
    if (on && !queue.canWriteToLibrary) Notice(stringResource(R.string.settings_queue_no_folder))
    SwitchRow(
        title = stringResource(R.string.settings_queue_delete),
        hint = stringResource(R.string.settings_queue_delete_hint),
        checked = settings.queueDeleteUnplayed,
        onChange = { onQueue(QueueAction.DeleteUnplayed(it)) },
        tag = SettingsTestTags.QUEUE_DELETE,
        enabled = on,
    )
    SwitchRow(
        title = stringResource(R.string.settings_queue_mobile_data),
        hint = stringResource(R.string.settings_queue_mobile_data_hint),
        checked = settings.queueUseMobileData,
        onChange = { onQueue(QueueAction.UseMobileData(it)) },
        tag = SettingsTestTags.QUEUE_MOBILE_DATA,
        enabled = on,
    )
    SuggestedChoice(settings.queueReplaceSuggested, enabled = on) { onQueue(QueueAction.ReplaceSuggested(it)) }
}

@Composable
private fun Notice(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = RiffColors.Paper)
}

/** "Música com mapa sugerido": baixar um mais parecido ou manter o que já tem. */
@Composable
private fun SuggestedChoice(replace: Boolean, enabled: Boolean, onChoose: (Boolean) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    ChoiceRow(
        stringResource(R.string.settings_queue_suggested),
        stringResource(suggestedName(replace)),
        enabled = enabled,
    ) { choosing = true }
    if (!choosing) return
    AlertDialog(
        onDismissRequest = { choosing = false },
        title = { Text(stringResource(R.string.settings_queue_suggested)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                for (option in listOf(true, false)) {
                    Row(
                        Modifier.fillMaxWidth().clickable(
                            onClick = withMenuSound(MenuSound.NAVIGATE) {
                                choosing = false
                                if (option != replace) onChoose(option)
                            },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        RadioButton(
                            selected = option == replace,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = RiffColors.TourRed),
                        )
                        Text(stringResource(suggestedName(option)), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = withMenuSound(MenuSound.BACK) { choosing = false }) {
                Text(stringResource(R.string.settings_look_close))
            }
        },
    )
}

private fun suggestedName(replace: Boolean) =
    if (replace) R.string.settings_queue_suggested_replace else R.string.settings_queue_suggested_keep
