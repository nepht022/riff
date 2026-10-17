package com.rodrigomachado.riff.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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

/** "Músicas com mapas já baixados": as duas escolhas à mostra, só uma marcada. */
@Composable
private fun SuggestedChoice(replace: Boolean, enabled: Boolean, onChoose: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else DISABLED_ALPHA).padding(vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            stringResource(R.string.settings_queue_suggested),
            style = MaterialTheme.typography.titleMedium,
            color = RiffColors.Paper,
        )
        Column(Modifier.selectableGroup()) {
            for (option in listOf(true, false)) {
                Row(
                    Modifier.fillMaxWidth().selectable(
                        selected = option == replace,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = withMenuSound(MenuSound.NAVIGATE) { if (option != replace) onChoose(option) },
                    ).padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    RadioButton(
                        selected = option == replace,
                        onClick = null,
                        enabled = enabled,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = RiffColors.TourRed,
                            unselectedColor = RiffColors.PaperDim,
                        ),
                    )
                    Text(
                        stringResource(suggestedName(option)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = RiffColors.Paper,
                    )
                }
            }
        }
    }
}

private fun suggestedName(replace: Boolean) =
    if (replace) R.string.settings_queue_suggested_replace else R.string.settings_queue_suggested_keep
