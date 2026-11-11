package com.rodrigomachado.riff.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.BackRow
import com.rodrigomachado.riff.core.ui.component.KeyHint
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.WithPcKeyHints
import com.rodrigomachado.riff.core.ui.component.backHint
import com.rodrigomachado.riff.core.ui.component.chooseHint
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.moveHint
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.input.tabLeaves
import com.rodrigomachado.riff.core.ui.input.upDownLeave
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.report.resources.Res
import com.rodrigomachado.riff.feature.report.resources.report_back
import com.rodrigomachado.riff.feature.report.resources.report_contact_hint
import com.rodrigomachado.riff.feature.report.resources.report_contact_invalid
import com.rodrigomachado.riff.feature.report.resources.report_contact_label
import com.rodrigomachado.riff.feature.report.resources.report_custom_category
import com.rodrigomachado.riff.feature.report.resources.report_device_title
import com.rodrigomachado.riff.feature.report.resources.report_done
import com.rodrigomachado.riff.feature.report.resources.report_failed
import com.rodrigomachado.riff.feature.report.resources.report_keys_next
import com.rodrigomachado.riff.feature.report.resources.report_kind_other
import com.rodrigomachado.riff.feature.report.resources.report_kind_problem
import com.rodrigomachado.riff.feature.report.resources.report_kind_suggestion
import com.rodrigomachado.riff.feature.report.resources.report_not_configured
import com.rodrigomachado.riff.feature.report.resources.report_privacy
import com.rodrigomachado.riff.feature.report.resources.report_send
import com.rodrigomachado.riff.feature.report.resources.report_sending
import com.rodrigomachado.riff.feature.report.resources.report_sent
import com.rodrigomachado.riff.feature.report.resources.report_text_label
import com.rodrigomachado.riff.feature.report.resources.report_text_label_other
import com.rodrigomachado.riff.feature.report.resources.report_text_label_suggestion
import com.rodrigomachado.riff.feature.report.resources.report_title
import com.rodrigomachado.riff.feature.report.resources.report_wait
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

object ReportTestTags {
    const val TEXT = "report_text"
    const val CUSTOM_CATEGORY = "report_custom_category"
    const val CONTACT = "report_contact"
    const val INCLUDE_DEVICE = "report_include_device"
    const val SEND = "report_send"
    const val DONE = "report_done"
}

@Composable
fun ReportScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ReportViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportContent(
        state = state,
        onKind = viewModel::setKind,
        onCategory = viewModel::setCategory,
        onCustomCategory = viewModel::setCustomCategory,
        onText = viewModel::setText,
        onContact = viewModel::setContact,
        onIncludeDevice = viewModel::setIncludeDevice,
        onSend = viewModel::send,
        onBack = onBack,
        modifier = modifier,
    )
}

/** Relatar um problema, uma sugestão ou outra coisa: escrito no app, enviado ao desenvolvedor. */
@Composable
fun ReportContent(
    state: ReportUiState,
    onKind: (ReportKind) -> Unit,
    onText: (String) -> Unit,
    onContact: (String) -> Unit,
    onIncludeDevice: (Boolean) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onCategory: (ReportCategory) -> Unit = {},
    onCustomCategory: (String) -> Unit = {},
) {
    // No PC, Tab sai do campo do texto (onde ↑/↓ andam nas linhas).
    val keys = listOf(moveHint(), KeyHint("Tab", pad = null, label = stringResource(Res.string.report_keys_next)))
    WithPcKeyHints(keys + chooseHint() + backHint(), modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Sem som aqui: a navegação toca o de voltar ao sair da tela (com ele, soava duas vezes).
            BackRow(onBack = onBack, text = stringResource(Res.string.report_back))
            PosterTitle(stringResource(Res.string.report_title), style = MaterialTheme.typography.displaySmall)
            if (state.status == ReportStatus.SENT) {
                Text(
                    stringResource(Res.string.report_sent),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.titleLarge,
                    color = RiffColors.Paper,
                )
                PosterButton(
                    stringResource(Res.string.report_done),
                    onClick = onBack,
                    modifier = Modifier.testTag(ReportTestTags.DONE),
                )
            } else {
                val inputs = ReportInputs(onKind, onCategory, onCustomCategory, onText, onContact, onIncludeDevice)
                ReportForm(state, inputs)
                StatusMessage(state.status)
                PosterButton(
                    stringResource(
                        if (state.status == ReportStatus.SENDING) Res.string.report_sending else Res.string.report_send,
                    ),
                    onClick = onSend,
                    enabled = state.canSend,
                    sound = MenuSound.CONFIRM,
                    modifier = Modifier.testTag(ReportTestTags.SEND).padding(bottom = Spacing.lg),
                )
            }
        }
    }
}

/** O que a pessoa pode mudar no formulário. */
private class ReportInputs(
    val onKind: (ReportKind) -> Unit,
    val onCategory: (ReportCategory) -> Unit,
    val onCustomCategory: (String) -> Unit,
    val onText: (String) -> Unit,
    val onContact: (String) -> Unit,
    val onIncludeDevice: (Boolean) -> Unit,
)

@Composable
private fun ReportForm(state: ReportUiState, inputs: ReportInputs) {
    // Quebra a linha com a fonte grande do Android em vez de cortar o terceiro tipo.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for (kind in ReportKind.entries) {
            FilterChip(
                selected = kind == state.kind,
                onClick = withMenuSound(MenuSound.NAVIGATE) { inputs.onKind(kind) },
                label = { Text(kindName(kind)) },
                modifier = Modifier.focusHighlight(FilterChipDefaults.shape),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = RiffColors.TourRed,
                    selectedLabelColor = RiffColors.Paper,
                    labelColor = RiffColors.PaperDim,
                ),
            )
        }
    }
    val categories = ReportCategory.forKind(state.kind)
    if (categories.isNotEmpty()) CategoryField(categories, state.category, inputs.onCategory)
    OutlinedTextField(
        value = state.text,
        onValueChange = inputs.onText,
        label = { Text(stringResource(textLabel(state.kind))) },
        minLines = TEXT_LINES,
        // No PC ↑/↓ andam no texto (várias linhas); Tab e Shift+Tab saem do campo.
        modifier = Modifier.tabLeaves().fillMaxWidth().testTag(ReportTestTags.TEXT),
    )
    if (state.kind == ReportKind.OTHER) {
        OutlinedTextField(
            value = state.customCategory,
            onValueChange = inputs.onCustomCategory,
            label = { Text(stringResource(Res.string.report_custom_category)) },
            singleLine = true,
            // Uma linha: no PC ↑/↓ saem do campo, como no filtro da Biblioteca.
            modifier = Modifier.upDownLeave().fillMaxWidth().testTag(ReportTestTags.CUSTOM_CATEGORY),
        )
    }
    OutlinedTextField(
        value = state.contact,
        onValueChange = inputs.onContact,
        label = { Text(stringResource(Res.string.report_contact_label)) },
        supportingText = {
            Text(
                stringResource(
                    if (state.contactInvalid) Res.string.report_contact_invalid else Res.string.report_contact_hint,
                ),
            )
        },
        isError = state.contactInvalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.upDownLeave().fillMaxWidth().testTag(ReportTestTags.CONTACT),
    )
    // Para onde vai e para que serve o email, antes de enviar (LGPD).
    Text(
        stringResource(Res.string.report_privacy),
        style = MaterialTheme.typography.bodySmall,
        color = RiffColors.PaperDim,
    )
    DeviceRow(state.device, state.includeDevice, inputs.onIncludeDevice)
}

/** O interruptor e, embaixo, exatamente o que vai junto. */
@Composable
private fun DeviceRow(device: DeviceInfo, include: Boolean, onChange: (Boolean) -> Unit) {
    // A linha toda liga e desliga, e o leitor de tela lê o nome junto com o estado.
    Row(
        Modifier.fillMaxWidth().focusHighlight().toggleable(
            value = include,
            role = Role.Switch,
            onValueChange = withMenuSound(MenuSound.NAVIGATE, onChange),
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.report_device_title),
                style = MaterialTheme.typography.titleMedium,
                color = RiffColors.Paper,
            )
            Text(
                device.summary,
                style = MaterialTheme.typography.bodySmall,
                color = RiffColors.PaperDim,
            )
        }
        Switch(
            checked = include,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RiffColors.Paper,
                checkedTrackColor = RiffColors.TourRed,
                uncheckedThumbColor = RiffColors.PaperDim,
                uncheckedTrackColor = RiffColors.StageBlack,
                uncheckedBorderColor = RiffColors.PaperDim,
            ),
            modifier = Modifier.testTag(ReportTestTags.INCLUDE_DEVICE),
        )
    }
}

@Composable
private fun StatusMessage(status: ReportStatus) {
    val message = when (status) {
        ReportStatus.FAILED -> Res.string.report_failed
        ReportStatus.WAIT -> Res.string.report_wait
        ReportStatus.NOT_CONFIGURED -> Res.string.report_not_configured
        ReportStatus.EDITING, ReportStatus.SENDING, ReportStatus.SENT -> null
    } ?: return
    // O leitor de tela anuncia sozinho (a mensagem aparece longe do botão Enviar).
    Text(
        stringResource(message),
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium,
        color = RiffColors.Paper,
    )
}

private fun textLabel(kind: ReportKind): StringResource = when (kind) {
    ReportKind.PROBLEM -> Res.string.report_text_label
    ReportKind.SUGGESTION -> Res.string.report_text_label_suggestion
    ReportKind.OTHER -> Res.string.report_text_label_other
}

@Composable
private fun kindName(kind: ReportKind): String = stringResource(
    when (kind) {
        ReportKind.PROBLEM -> Res.string.report_kind_problem
        ReportKind.SUGGESTION -> Res.string.report_kind_suggestion
        ReportKind.OTHER -> Res.string.report_kind_other
    },
)

private const val TEXT_LINES = 5
