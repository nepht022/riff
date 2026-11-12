package com.rodrigomachado.riff.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.AppLanguage
import com.rodrigomachado.riff.core.model.ControlAction
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.SeparationUsage
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.component.WithPcKeyHints
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.input.LocalGamepad
import com.rodrigomachado.riff.core.ui.input.upDownLeave
import com.rodrigomachado.riff.core.ui.stage.HighwayTexture
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.settings.resources.Res
import com.rodrigomachado.riff.feature.settings.resources.settings_calibrate
import com.rodrigomachado.riff.feature.settings.resources.settings_chart_image
import com.rodrigomachado.riff.feature.settings.resources.settings_chart_image_hint
import com.rodrigomachado.riff.feature.settings.resources.settings_left_handed
import com.rodrigomachado.riff.feature.settings.resources.settings_left_handed_hint
import com.rodrigomachado.riff.feature.settings.resources.settings_master_volume
import com.rodrigomachado.riff.feature.settings.resources.settings_master_volume_hint
import com.rodrigomachado.riff.feature.settings.resources.settings_menu_sounds
import com.rodrigomachado.riff.feature.settings.resources.settings_menu_sounds_hint
import com.rodrigomachado.riff.feature.settings.resources.settings_miss_volume
import com.rodrigomachado.riff.feature.settings.resources.settings_not_calibrated
import com.rodrigomachado.riff.feature.settings.resources.settings_note_speed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.text.NumberFormat
import kotlin.math.roundToInt

object SettingsTestTags {
    const val MASTER_VOLUME = "settings_master_volume"
    const val LEFT_HANDED = "settings_left_handed"
    const val MENU_SOUNDS = "settings_menu_sounds"
    const val ASK_ALWAYS = "settings_ask_separation_always"
    const val ONLY_CHARGING = "settings_separate_only_charging"
    const val CHART_IMAGE = "settings_chart_image"
    const val QUEUE_PREFETCH = "settings_queue_prefetch"
    const val QUEUE_DELETE = "settings_queue_delete"
    const val QUEUE_MOBILE_DATA = "settings_queue_mobile_data"

    /** A coluna das abas à esquerda (só na janela larga do PC). */
    const val TABS_COLUMN = "settings_tabs_column"

    /** As opções da aba aberta, à direita da coluna das abas (só na janela larga do PC). */
    const val OPTIONS = "settings_options"
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCalibrate: () -> Unit,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    /** Trocar o nome no ranking (o diálogo é do app, o mesmo da primeira abertura). */
    onRankingName: () -> Unit = {},
    /** Abrir as licenças e a privacidade; `null` esconde a linha. */
    onLicenses: (() -> Unit)? = {},
    /** As abas mostradas, na ordem da barra (no celular, sem a dos Controles). */
    tabs: List<SettingsTab> = SettingsTab.entries - SettingsTab.CONTROLS,
) {
    // O controle do PC; no celular não há (nem a aba).
    val padConnected by (LocalGamepad.current?.connected ?: remember { MutableStateFlow(false) })
        .collectAsStateWithLifecycle()
    val rankingName by viewModel.rankingName.collectAsStateWithLifecycle()
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val usage by viewModel.separationUsage.collectAsStateWithLifecycle()
    val refreshLanguage = rememberLanguageRefresh()
    val current = settings
    if (current == null) {
        LoadingState(modifier)
        return
    }
    SettingsContent(
        settings = current,
        onMasterVolume = viewModel::setMasterVolume,
        onMissVolume = viewModel::setMissVolume,
        onNoteSpeed = viewModel::setNoteSpeed,
        onLeftHanded = viewModel::setLeftHanded,
        onMenuSounds = viewModel::setMenuSounds,
        onPreviewMiss = viewModel::previewMiss,
        onCalibrate = onCalibrate,
        onBack = onBack,
        modifier = modifier,
        separationUsage = usage,
        onDeleteSeparations = viewModel::deleteSeparations,
        onAskAlways = viewModel::setAskSeparationAlways,
        onSeparateOnlyCharging = viewModel::setSeparateOnlyCharging,
        onTrackLook = viewModel::setTrackLook,
        onShowChartImage = viewModel::setShowChartImage,
        padConnected = padConnected,
        onStrum = viewModel::setStrum,
        onControlKey = viewModel::setKey,
        onControlPad = viewModel::setPad,
        onControlDefaults = viewModel::resetControls,
        rankingName = rankingName,
        onRankingName = onRankingName,
        onLicenses = onLicenses,
        onLanguage = {
            viewModel.setLanguage(it)
            refreshLanguage()
        },
        tabs = tabs,
    )
}

@Composable
fun SettingsContent(
    settings: Settings,
    onMasterVolume: (Float) -> Unit,
    onMissVolume: (Float) -> Unit,
    onNoteSpeed: (Float) -> Unit,
    onLeftHanded: (Boolean) -> Unit,
    onMenuSounds: (Boolean) -> Unit,
    onPreviewMiss: () -> Unit,
    onCalibrate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    separationUsage: SeparationUsage = SeparationUsage(0, 0),
    onDeleteSeparations: () -> Unit = {},
    onAskAlways: (Boolean) -> Unit = {},
    onSeparateOnlyCharging: (Boolean) -> Unit = {},
    onTrackLook: (TrackLookChange) -> Unit = {},
    onShowChartImage: (Boolean) -> Unit = {},
    onLanguage: (AppLanguage) -> Unit = {},
    initialTab: SettingsTab = SettingsTab.GENERAL,
    rankingName: RankingName? = null,
    onRankingName: () -> Unit = {},
    /** `null` esconde a linha das licenças (sem a tela delas). */
    onLicenses: (() -> Unit)? = {},
    /** Há um controle ligado no PC (a aba Controles avisa quando não há). */
    padConnected: Boolean = false,
    onStrum: (Boolean) -> Unit = {},
    onControlKey: (ControlAction, Long) -> Unit = { _, _ -> },
    onControlPad: (ControlAction, String) -> Unit = { _, _ -> },
    onControlDefaults: () -> Unit = {},
    /** As abas mostradas, na ordem da barra (no celular, sem a dos Controles). */
    tabs: List<SettingsTab> = SettingsTab.entries - SettingsTab.CONTROLS,
) {
    // Sempre abre em Geral; a aba sobrevive à tela refeita (troca de idioma, rotação).
    var tab by rememberSaveable { mutableStateOf(initialTab.takeIf { it in tabs } ?: tabs.first()) }
    // O idioma em que a tela está: no celular acompanha também uma troca feita nas configurações do sistema.
    val language = currentAppLanguage()
    // O conteúdo da aba aberta; [wide] = no PC largo (as licenças ficam no pé da coluna das abas, não na Geral).
    val tabContent: @Composable (wide: Boolean) -> Unit = { wide ->
        when (tab) {
            SettingsTab.GENERAL -> GeneralTab(
                language,
                onLanguage,
                settings.menuSounds,
                onMenuSounds,
                rankingName,
                onRankingName,
                onLicenses.takeUnless { wide },
            )

            SettingsTab.SOUNDS -> SoundsTab(settings, onMasterVolume, onMissVolume, onPreviewMiss)

            SettingsTab.GAME -> GameTab(settings, onNoteSpeed, onLeftHanded, onCalibrate)

            SettingsTab.CONTROLS -> ControlsTab(
                settings.controls,
                padConnected,
                onStrum,
                onControlKey,
                onControlPad,
                onControlDefaults,
            )

            SettingsTab.TRACK_LOOK -> TrackLookTab(settings, onTrackLook, onShowChartImage)

            SettingsTab.AI -> AiSeparationSection(
                separationUsage,
                onDeleteSeparations,
                settings,
                onAskAlways,
                onSeparateOnlyCharging,
            )
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_MIN_WIDTH
        WithPcKeyHints(if (wide) wideSettingsKeys() else phoneSettingsKeys()) {
            if (wide) {
                WideSettings(tabs, tab, { tab = it }, onBack, onLicenses) { tabContent(true) }
            } else {
                PhoneSettings(tabs, tab, { tab = it }, onBack) { tabContent(false) }
            }
        }
    }
}

@Composable
private fun SoundsTab(
    settings: Settings,
    onMasterVolume: (Float) -> Unit,
    onMissVolume: (Float) -> Unit,
    onPreviewMiss: () -> Unit,
) {
    SettingSlider(
        label = stringResource(Res.string.settings_master_volume),
        value = settings.masterVolume,
        text = ::percent,
        onCommit = onMasterVolume,
        hint = stringResource(Res.string.settings_master_volume_hint),
        modifier = Modifier.testTag(SettingsTestTags.MASTER_VOLUME),
    )
    // Sem "Som de acerto": as partidas não tocam mais som ao acertar (a música toca inteira, ou a guitarra separada
    // é a resposta).
    SettingSlider(
        label = stringResource(Res.string.settings_miss_volume),
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
        label = stringResource(Res.string.settings_note_speed),
        value = settings.noteSpeed,
        text = ::speed,
        onCommit = onNoteSpeed,
        range = Settings.MIN_NOTE_SPEED..Settings.MAX_NOTE_SPEED,
        steps = SPEED_STEPS,
    )
    SwitchRow(
        title = stringResource(Res.string.settings_left_handed),
        hint = stringResource(Res.string.settings_left_handed_hint),
        checked = settings.leftHanded,
        onChange = onLeftHanded,
        tag = SettingsTestTags.LEFT_HANDED,
    )
    CalibrationRow(settings.audioOffsetMs, onCalibrate)
}

@Composable
private fun TrackLookTab(
    settings: Settings,
    onTrackLook: (TrackLookChange) -> Unit,
    onShowChartImage: (Boolean) -> Unit,
) {
    // As miniaturas das listas, geradas fora da thread da tela antes de a lista abrir.
    LaunchedEffect(Unit) { withContext(Dispatchers.Default) { HighwayTexture.prepareAll() } }
    TrackLookSection(settings.trackLook, onTrackLook)
    SwitchRow(
        title = stringResource(Res.string.settings_chart_image),
        hint = stringResource(Res.string.settings_chart_image_hint),
        checked = settings.showChartImage,
        onChange = onShowChartImage,
        tag = SettingsTestTags.CHART_IMAGE,
    )
}

/**
 * O valor acompanha o dedo; só é gravado (e o som de prévia toca) quando o jogador solta. No PC, ←/→ mudam o valor
 * (o próprio Slider, gravando ao soltar a tecla) e ↑/↓ saem dele para a linha de cima ou de baixo.
 */
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
    val wide = LocalWideSettings.current
    Column(Modifier.settingBlock(wide).padding(top = if (wide) Spacing.md else 0.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = rowTitleStyle(), color = RiffColors.Paper)
            Text(text(shown), style = rowTitleStyle(), color = RiffColors.Paper)
        }
        hint?.let { Text(it, style = rowHintStyle(), color = RiffColors.PaperDim) }
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
            modifier = modifier.focusHighlight().upDownLeave(),
        )
    }
}

@Composable
private fun CalibrationRow(audioOffsetMs: Int, onCalibrate: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().focusHighlight().clickable(
            onClick = withMenuSound(MenuSound.NAVIGATE, onCalibrate),
        ).settingBlock(LocalWideSettings.current).padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.settings_calibrate),
            style = rowTitleStyle(),
            color = RiffColors.Paper,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (audioOffsetMs == 0) stringResource(Res.string.settings_not_calibrated) else "${-audioOffsetMs} ms",
            style = rowTitleStyle(),
            color = RiffColors.PaperDim,
        )
    }
}

/** Sem som próprio: ao desligar, ainda estariam ligados; o app soa quando eles voltam a ser ligados. */
@Composable
internal fun MenuSoundsRow(checked: Boolean, onChange: (Boolean) -> Unit, switchModifier: Modifier = Modifier) {
    SwitchRow(
        title = stringResource(Res.string.settings_menu_sounds),
        hint = stringResource(Res.string.settings_menu_sounds_hint),
        checked = checked,
        onChange = onChange,
        tag = SettingsTestTags.MENU_SOUNDS,
        sound = null,
        switchModifier = switchModifier,
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
    /** Do interruptor (o que pega o foco): por exemplo, para as setas do PC acharem ele. */
    switchModifier: Modifier = Modifier,
) {
    val wide = LocalWideSettings.current
    Row(
        Modifier.fillMaxWidth().settingBlock(wide).padding(vertical = if (wide) Spacing.md else Spacing.sm)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = if (wide) Spacing.md else 0.dp)) {
            Text(
                title,
                style = rowTitleStyle(),
                color = RiffColors.Paper,
            )
            Text(
                hint,
                style = rowHintStyle(),
                color = RiffColors.PaperDim,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = if (sound == null) onChange else withMenuSound(sound, onChange),
            enabled = enabled,
            // Desligado, as cores ficam as mesmas: quem esmaece é a linha inteira (senão esmaecia duas vezes).
            colors = SwitchDefaults.colors(
                checkedThumbColor = RiffColors.Paper,
                checkedTrackColor = RiffColors.TourRed,
                uncheckedThumbColor = RiffColors.PaperDim,
                uncheckedTrackColor = RiffColors.StageBlack,
                uncheckedBorderColor = RiffColors.PaperDim,
                disabledCheckedThumbColor = RiffColors.Paper,
                disabledCheckedTrackColor = RiffColors.TourRed,
                disabledUncheckedThumbColor = RiffColors.PaperDim,
                disabledUncheckedTrackColor = RiffColors.StageBlack,
                disabledUncheckedBorderColor = RiffColors.PaperDim,
            ),
            modifier = switchModifier.testTag(tag).focusHighlight(CircleShape),
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
