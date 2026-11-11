package com.rodrigomachado.riff.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.AppLanguage
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.BackRow
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.SeparationIndicator
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.input.menuKeys
import com.rodrigomachado.riff.core.ui.input.menuKeysEnabled
import com.rodrigomachado.riff.core.ui.theme.MenuColumnMaxWidth
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.settings.resources.Res
import com.rodrigomachado.riff.feature.settings.resources.settings_back
import com.rodrigomachado.riff.feature.settings.resources.settings_game
import com.rodrigomachado.riff.feature.settings.resources.settings_language
import com.rodrigomachado.riff.feature.settings.resources.settings_licenses
import com.rodrigomachado.riff.feature.settings.resources.settings_licenses_open
import com.rodrigomachado.riff.feature.settings.resources.settings_look_close
import com.rodrigomachado.riff.feature.settings.resources.settings_ranking_choose
import com.rodrigomachado.riff.feature.settings.resources.settings_ranking_name
import com.rodrigomachado.riff.feature.settings.resources.settings_sounds
import com.rodrigomachado.riff.feature.settings.resources.settings_tab_ai
import com.rodrigomachado.riff.feature.settings.resources.settings_tab_controls
import com.rodrigomachado.riff.feature.settings.resources.settings_tab_general
import com.rodrigomachado.riff.feature.settings.resources.settings_tab_track_look
import com.rodrigomachado.riff.feature.settings.resources.settings_title
import org.jetbrains.compose.resources.stringResource

/** As abas das Configurações, na ordem da barra; a dos Controles é só do PC. */
enum class SettingsTab { GENERAL, SOUNDS, GAME, CONTROLS, TRACK_LOOK, AI }

/** O celular (e a janela estreita do PC): a coluna no meio, com as abas numa barra no topo. */
@Composable
internal fun PhoneSettings(
    tabs: List<SettingsTab>,
    tab: SettingsTab,
    onTab: (SettingsTab) -> Unit,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = MenuColumnMaxWidth).fillMaxSize().padding(horizontal = Spacing.md)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BackRow(onBack = onBack, text = stringResource(Res.string.settings_back))
                Spacer(Modifier.weight(1f))
                SeparationIndicator()
            }
            PosterTitle(stringResource(Res.string.settings_title), style = MaterialTheme.typography.displaySmall)
            SettingsTabBar(tabs, tab, onSelect = onTab)
            // Cada aba começa do topo.
            key(tab) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) { content() }
            }
        }
    }
}

/** Barra de abas que rola de lado se não couber; a escolhida fica clara e sublinhada de vermelho. */
@Composable
internal fun SettingsTabBar(tabs: List<SettingsTab>, selected: SettingsTab, onSelect: (SettingsTab) -> Unit) {
    val scroll = rememberScrollState()
    // Refeita numa aba fora da tela (celular estreito), a barra rola o mínimo para ela aparecer.
    val requesters = remember(tabs) { tabs.associateWith { BringIntoViewRequester() } }
    LaunchedEffect(selected) {
        withFrameNanos {} // depois da primeira medida: antes dela não há onde rolar
        requesters[selected]?.bringIntoView()
    }
    Row(
        // selectableGroup: o TalkBack diz "aba 2 de 6".
        Modifier.fillMaxWidth().horizontalScroll(scroll).selectableGroup().padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        for (tab in tabs) {
            val isSelected = tab == selected
            Column(
                Modifier.bringIntoViewRequester(requesters.getValue(tab)).focusHighlight().selectable(
                    selected = isSelected,
                    role = Role.Tab,
                    onClick = withMenuSound(MenuSound.NAVIGATE) { onSelect(tab) },
                ).padding(vertical = Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    tabTitle(tab),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) RiffColors.Paper else RiffColors.PaperDim,
                    textAlign = TextAlign.Center,
                )
                Box(
                    Modifier.padding(top = Spacing.xs).width(TAB_UNDERLINE_WIDTH).height(TAB_UNDERLINE_HEIGHT)
                        .background(if (isSelected) RiffColors.TourRed else Color.Transparent),
                )
            }
        }
    }
}

@Composable
internal fun tabTitle(tab: SettingsTab): String = stringResource(
    when (tab) {
        SettingsTab.GENERAL -> Res.string.settings_tab_general
        SettingsTab.SOUNDS -> Res.string.settings_sounds
        SettingsTab.GAME -> Res.string.settings_game
        SettingsTab.CONTROLS -> Res.string.settings_tab_controls
        SettingsTab.TRACK_LOOK -> Res.string.settings_tab_track_look
        SettingsTab.AI -> Res.string.settings_tab_ai
    },
)

/** O nome no ranking na aba Geral; [name] nulo = ainda sem nome. */
data class RankingName(val name: String?)

/** Geral: o idioma do jogo, o nome no ranking, os sons do menu e (se houver) as licenças e a privacidade. */
@Composable
internal fun GeneralTab(
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    menuSounds: Boolean,
    onMenuSounds: (Boolean) -> Unit,
    rankingName: RankingName? = null,
    onRankingName: () -> Unit = {},
    onLicenses: (() -> Unit)? = {},
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    // O interruptor dos sons fica à direita, entre linhas largas: no PC ↓ e ↑ das vizinhas pulariam ele.
    val menuSoundsFocus = remember { FocusRequester() }
    val aboveSwitch = pcArrow { down = menuSoundsFocus }
    ChoiceRow(
        stringResource(Res.string.settings_language),
        languageName(language),
        modifier = if (rankingName == null) aboveSwitch else Modifier,
    ) { choosing = true }
    rankingName?.let {
        ChoiceRow(
            stringResource(Res.string.settings_ranking_name),
            it.name ?: stringResource(Res.string.settings_ranking_choose),
            modifier = aboveSwitch,
            onClick = onRankingName,
        )
    }
    MenuSoundsRow(menuSounds, onMenuSounds, switchModifier = Modifier.focusRequester(menuSoundsFocus))
    onLicenses?.let {
        ChoiceRow(
            stringResource(Res.string.settings_licenses),
            stringResource(Res.string.settings_licenses_open),
            modifier = pcArrow { up = menuSoundsFocus },
            onClick = it,
        )
    }
    if (choosing) LanguageDialog(language, onLanguage) { choosing = false }
}

/** A escolha do idioma do jogo. */
@Composable
private fun LanguageDialog(language: AppLanguage, onLanguage: (AppLanguage) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,

        // No PC as setas andam pelo diálogo, e o Esc o fecha (sem chegar à tela de trás).

        modifier = Modifier.menuKeys(onBack = onClose),
        title = { Text(stringResource(Res.string.settings_language)) },
        text = {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                for (option in AppLanguage.entries) {
                    Row(
                        // Cada idioma é uma opção marcada ou não para o TalkBack, como o botão de rádio.
                        Modifier.fillMaxWidth().focusHighlight().selectable(
                            selected = option == language,
                            role = Role.RadioButton,
                            onClick = withMenuSound(MenuSound.NAVIGATE) {
                                onClose()
                                if (option != language) onLanguage(option)
                            },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        RadioButton(
                            selected = option == language,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = RiffColors.TourRed),
                        )
                        Text(languageName(option), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = withMenuSound(MenuSound.BACK, onClose),
                modifier = Modifier.focusHighlight(CircleShape),
            ) {
                Text(stringResource(Res.string.settings_look_close))
            }
        },
    )
}

/** Cada idioma no próprio idioma: quem trocar sem querer acha o caminho de volta. */
private fun languageName(language: AppLanguage): String = when (language) {
    AppLanguage.PORTUGUESE -> "Português"
    AppLanguage.ENGLISH -> "English"
}

private val TAB_UNDERLINE_WIDTH = 24.dp
private val TAB_UNDERLINE_HEIGHT = 3.dp

/** Só nos menus do PC: aonde as setas levam a partir desta linha (no celular, nada muda). */
private fun pcArrow(scope: FocusProperties.() -> Unit): Modifier =
    if (menuKeysEnabled) Modifier.focusProperties(scope) else Modifier
