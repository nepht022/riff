package com.rodrigomachado.riff.feature.settings

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.AppLanguage
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.SeparationUsage
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.model.TrackLook
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.SeparationIndicator
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import java.text.NumberFormat
import kotlin.math.roundToInt

object SettingsTestTags {
    const val MASTER_VOLUME = "settings_master_volume"
    const val LEFT_HANDED = "settings_left_handed"
    const val MENU_SOUNDS = "settings_menu_sounds"
    const val ASK_ALWAYS = "settings_ask_separation_always"
    const val CHART_IMAGE = "settings_chart_image"
    const val QUEUE_PREFETCH = "settings_queue_prefetch"
    const val QUEUE_DELETE = "settings_queue_delete"
    const val QUEUE_MOBILE_DATA = "settings_queue_mobile_data"
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCalibrate: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val usage by viewModel.separationUsage.collectAsStateWithLifecycle()
    val queueMessage by viewModel.queueMessage.collectAsStateWithLifecycle()
    val canWriteToLibrary by viewModel.canWriteToLibrary.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
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
        onMenuSounds = viewModel::setMenuSounds,
        onPreviewHit = viewModel::previewHit,
        onPreviewMiss = viewModel::previewMiss,
        onCalibrate = onCalibrate,
        onBack = onBack,
        modifier = modifier,
        separationUsage = usage,
        onDeleteSeparations = viewModel::deleteSeparations,
        onAskAlways = viewModel::setAskSeparationAlways,
        onTrackLook = viewModel::setTrackLook,
        onShowChartImage = viewModel::setShowChartImage,
        queue = QueueUiState(queueMessage, canWriteToLibrary),
        onQueue = viewModel::onQueue,
        onLanguage = {
            viewModel.setLanguage(it)
            // Antes do Android 13 o sistema não refaz a tela sozinho: refaz aqui, já no idioma novo.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) activity?.recreate()
        },
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
    onMenuSounds: (Boolean) -> Unit,
    onPreviewHit: () -> Unit,
    onPreviewMiss: () -> Unit,
    onCalibrate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    separationUsage: SeparationUsage = SeparationUsage(0, 0),
    onDeleteSeparations: () -> Unit = {},
    onAskAlways: (Boolean) -> Unit = {},
    onTrackLook: (TrackLook) -> Unit = {},
    onShowChartImage: (Boolean) -> Unit = {},
    onLanguage: (AppLanguage) -> Unit = {},
    queue: QueueUiState = QueueUiState(message = null, canWriteToLibrary = true),
    onQueue: (QueueAction) -> Unit = {},
    initialTab: SettingsTab = SettingsTab.GENERAL,
) {
    // Sempre abre em Geral; a aba sobrevive à tela refeita (troca de idioma, rotação).
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    // O idioma em que a tela está: acompanha também uma troca feita nas configurações do celular.
    val language = AppLanguage.fromTag(LocalConfiguration.current.locales[0].toLanguageTag())
    Column(modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.settings_back), color = RiffColors.Paper) }
            Spacer(Modifier.weight(1f))
            SeparationIndicator()
        }
        PosterTitle(stringResource(R.string.settings_title), style = MaterialTheme.typography.displaySmall)
        SettingsTabBar(tab, onSelect = { tab = it })
        // Cada aba começa do topo.
        key(tab) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                when (tab) {
                    SettingsTab.GENERAL -> GeneralTab(language, onLanguage, settings.menuSounds, onMenuSounds)

                    SettingsTab.SOUNDS -> SoundsTab(
                        settings,
                        onMasterVolume,
                        onHitVolume,
                        onMissVolume,
                        onPreviewHit,
                        onPreviewMiss,
                    )

                    SettingsTab.GAME -> GameTab(settings, onNoteSpeed, onLeftHanded, onCalibrate)

                    SettingsTab.TRACK_LOOK -> TrackLookTab(settings, onTrackLook, onShowChartImage)

                    SettingsTab.SPOTIFY -> SpotifyTab(settings, queue, onQueue)

                    SettingsTab.AI -> AiSeparationSection(
                        separationUsage,
                        onDeleteSeparations,
                        settings.askSeparationAlways,
                        onAskAlways,
                    )
                }
            }
        }
    }
}

@Composable
private fun SoundsTab(
    settings: Settings,
    onMasterVolume: (Float) -> Unit,
    onHitVolume: (Float) -> Unit,
    onMissVolume: (Float) -> Unit,
    onPreviewHit: () -> Unit,
    onPreviewMiss: () -> Unit,
) {
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
}

@Composable
private fun GameTab(
    settings: Settings,
    onNoteSpeed: (Float) -> Unit,
    onLeftHanded: (Boolean) -> Unit,
    onCalibrate: () -> Unit,
) {
    SettingSlider(
        label = stringResource(R.string.settings_note_speed),
        value = settings.noteSpeed,
        text = ::speed,
        onCommit = onNoteSpeed,
        range = Settings.MIN_NOTE_SPEED..Settings.MAX_NOTE_SPEED,
        steps = SPEED_STEPS,
    )
    SwitchRow(
        title = stringResource(R.string.settings_left_handed),
        hint = stringResource(R.string.settings_left_handed_hint),
        checked = settings.leftHanded,
        onChange = onLeftHanded,
        tag = SettingsTestTags.LEFT_HANDED,
    )
    CalibrationRow(settings.audioOffsetMs, onCalibrate)
}

@Composable
private fun TrackLookTab(settings: Settings, onTrackLook: (TrackLook) -> Unit, onShowChartImage: (Boolean) -> Unit) {
    TrackLookSection(settings.trackLook, onTrackLook)
    SwitchRow(
        title = stringResource(R.string.settings_chart_image),
        hint = stringResource(R.string.settings_chart_image_hint),
        checked = settings.showChartImage,
        onChange = onShowChartImage,
        tag = SettingsTestTags.CHART_IMAGE,
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
        Modifier.fillMaxWidth().clickable(
            onClick = withMenuSound(MenuSound.NAVIGATE, onCalibrate),
        ).padding(vertical = Spacing.md),
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

/** Sem som próprio: ao desligar, ainda estariam ligados; o app soa quando eles voltam a ser ligados. */
@Composable
internal fun MenuSoundsRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    SwitchRow(
        title = stringResource(R.string.settings_menu_sounds),
        hint = stringResource(R.string.settings_menu_sounds_hint),
        checked = checked,
        onChange = onChange,
        tag = SettingsTestTags.MENU_SOUNDS,
        sound = null,
    )
}

@Composable
internal fun SwitchRow(
    title: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    tag: String,
    sound: MenuSound? = MenuSound.NAVIGATE,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.sm).alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = RiffColors.Paper,
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = RiffColors.PaperDim,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = if (sound == null) onChange else withMenuSound(sound, onChange),
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RiffColors.Paper,
                checkedTrackColor = RiffColors.TourRed,
                uncheckedThumbColor = RiffColors.PaperDim,
                uncheckedTrackColor = RiffColors.StageBlack,
                uncheckedBorderColor = RiffColors.PaperDim,
            ),
            modifier = Modifier.testTag(tag),
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
internal const val DISABLED_ALPHA = 0.4f
