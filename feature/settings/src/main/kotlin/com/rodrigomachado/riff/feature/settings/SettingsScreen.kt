package com.rodrigomachado.riff.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import java.text.NumberFormat
import kotlin.math.roundToInt

object SettingsTestTags {
    const val MASTER_VOLUME = "settings_master_volume"
    const val LEFT_HANDED = "settings_left_handed"
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCalibrate: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val current = settings
    if (current == null) {
        LoadingState(modifier)
        return
    }
    SettingsContent(
        settings = current,
        onMasterVolume = viewModel::setMasterVolume,
        onHitVolume = viewModel::setHitVolume,
        onMissVolume = viewModel::setMissVolume,
        onNoteSpeed = viewModel::setNoteSpeed,
        onLeftHanded = viewModel::setLeftHanded,
        onPreviewHit = viewModel::previewHit,
        onPreviewMiss = viewModel::previewMiss,
        onCalibrate = onCalibrate,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun SettingsContent(
    settings: Settings,
    onMasterVolume: (Float) -> Unit,
    onHitVolume: (Float) -> Unit,
    onMissVolume: (Float) -> Unit,
    onNoteSpeed: (Float) -> Unit,
    onLeftHanded: (Boolean) -> Unit,
    onPreviewHit: () -> Unit,
    onPreviewMiss: () -> Unit,
    onCalibrate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.settings_back), color = RiffColors.Paper) }
        PosterTitle(stringResource(R.string.settings_title), style = MaterialTheme.typography.displaySmall)
        Section(stringResource(R.string.settings_sounds))
        SettingSlider(
            label = stringResource(R.string.settings_master_volume),
            value = settings.masterVolume,
            text = ::percent,
            onCommit = onMasterVolume,
            hint = stringResource(R.string.settings_master_volume_hint),
            modifier = Modifier.testTag(SettingsTestTags.MASTER_VOLUME),
        )
        SettingSlider(
            label = stringResource(R.string.settings_hit_volume),
            value = settings.hitVolume,
            text = ::percent,
            onCommit = {
                onHitVolume(it)
                onPreviewHit()
            },
        )
        SettingSlider(
            label = stringResource(R.string.settings_miss_volume),
            value = settings.missVolume,
            text = ::percent,
            onCommit = {
                onMissVolume(it)
                onPreviewMiss()
            },
        )
        Section(stringResource(R.string.settings_game))
        SettingSlider(
            label = stringResource(R.string.settings_note_speed),
            value = settings.noteSpeed,
            text = ::speed,
            onCommit = onNoteSpeed,
            range = Settings.MIN_NOTE_SPEED..Settings.MAX_NOTE_SPEED,
            steps = SPEED_STEPS,
        )
        LeftHandedRow(settings.leftHanded, onLeftHanded)
        CalibrationRow(settings.audioOffsetMs, onCalibrate)
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = RiffColors.PaperDim,
        modifier = Modifier.padding(top = Spacing.md),
    )
}

/** O valor acompanha o dedo; só é gravado (e o som de prévia toca) quando o jogador solta. */
@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    text: (Float) -> String,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = VOLUME_STEPS,
    hint: String? = null,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: value
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = RiffColors.Paper)
            Text(text(shown), style = MaterialTheme.typography.titleMedium, color = RiffColors.Paper)
        }
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RiffColors.PaperDim) }
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let(onCommit)
                dragging = null
            },
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = RiffColors.Paper,
                activeTrackColor = RiffColors.TourRed,
                inactiveTrackColor = RiffColors.PaperDim.copy(alpha = INACTIVE_ALPHA),
                activeTickColor = RiffColors.Paper,
                inactiveTickColor = RiffColors.PaperDim,
            ),
            modifier = modifier,
        )
    }
}

@Composable
private fun CalibrationRow(audioOffsetMs: Int, onCalibrate: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onCalibrate).padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.settings_calibrate),
            style = MaterialTheme.typography.titleMedium,
            color = RiffColors.Paper,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (audioOffsetMs == 0) stringResource(R.string.settings_not_calibrated) else "${-audioOffsetMs} ms",
            style = MaterialTheme.typography.titleMedium,
            color = RiffColors.PaperDim,
        )
    }
}

@Composable
private fun LeftHandedRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.settings_left_handed),
                style = MaterialTheme.typography.titleMedium,
                color = RiffColors.Paper,
            )
            Text(
                stringResource(R.string.settings_left_handed_hint),
                style = MaterialTheme.typography.bodySmall,
                color = RiffColors.PaperDim,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RiffColors.Paper,
                checkedTrackColor = RiffColors.TourRed,
                uncheckedThumbColor = RiffColors.PaperDim,
                uncheckedTrackColor = RiffColors.StageBlack,
                uncheckedBorderColor = RiffColors.PaperDim,
            ),
            modifier = Modifier.testTag(SettingsTestTags.LEFT_HANDED),
        )
    }
}

private fun percent(value: Float): String = "${(value * PERCENT).roundToInt()}%"

private fun speed(value: Float): String {
    val format = NumberFormat.getNumberInstance()
    format.minimumFractionDigits = 1
    format.maximumFractionDigits = 2
    return format.format(value) + "×"
}

private const val PERCENT = 100
private const val VOLUME_STEPS = 19 // de 5 em 5%
private const val SPEED_STEPS = 5 // 0,5 a 2,0 de 0,25 em 0,25
private const val INACTIVE_ALPHA = 0.3f
