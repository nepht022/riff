package com.rodrigomachado.riff.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.AppLanguage
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

/** As abas das Configurações, na ordem da barra. */
enum class SettingsTab { GENERAL, SOUNDS, GAME, TRACK_LOOK, AI, SPOTIFY }

/** Barra de abas que rola de lado se não couber; a escolhida fica clara e sublinhada de vermelho. */
@Composable
internal fun SettingsTabBar(selected: SettingsTab, onSelect: (SettingsTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        for (tab in SettingsTab.entries) {
            val isSelected = tab == selected
            Column(
                Modifier.selectable(
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
                        .background(if (isSelected) RiffColors.TourRed else RiffColors.StageBlack.copy(alpha = 0f)),
                )
            }
        }
    }
}

@Composable
private fun tabTitle(tab: SettingsTab): String = stringResource(
    when (tab) {
        SettingsTab.GENERAL -> R.string.settings_tab_general
        SettingsTab.SOUNDS -> R.string.settings_sounds
        SettingsTab.GAME -> R.string.settings_game
        SettingsTab.TRACK_LOOK -> R.string.settings_tab_track_look
        SettingsTab.AI -> R.string.settings_tab_ai
        SettingsTab.SPOTIFY -> R.string.settings_tab_spotify
    },
)

/** O nome no ranking na aba Geral; [name] nulo = ainda sem nome. */
data class RankingName(val name: String?)

/** Geral: o idioma do jogo, o nome no ranking, os sons do menu e as licenças e a privacidade. */
@Composable
internal fun GeneralTab(
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    menuSounds: Boolean,
    onMenuSounds: (Boolean) -> Unit,
    rankingName: RankingName? = null,
    onRankingName: () -> Unit = {},
    onLicenses: () -> Unit = {},
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    ChoiceRow(stringResource(R.string.settings_language), languageName(language)) { choosing = true }
    rankingName?.let {
        ChoiceRow(
            stringResource(R.string.settings_ranking_name),
            it.name ?: stringResource(R.string.settings_ranking_choose),
            onClick = onRankingName,
        )
    }
    MenuSoundsRow(menuSounds, onMenuSounds)
    ChoiceRow(
        stringResource(R.string.settings_licenses),
        stringResource(R.string.settings_licenses_open),
        onClick = onLicenses,
    )
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    for (option in AppLanguage.entries) {
                        Row(
                            Modifier.fillMaxWidth().clickable(
                                onClick = withMenuSound(MenuSound.NAVIGATE) {
                                    choosing = false
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
                TextButton(onClick = withMenuSound(MenuSound.BACK) { choosing = false }) {
                    Text(stringResource(R.string.settings_look_close))
                }
            },
        )
    }
}

/** Cada idioma no próprio idioma: quem trocar sem querer acha o caminho de volta. */
private fun languageName(language: AppLanguage): String = when (language) {
    AppLanguage.PORTUGUESE -> "Português"
    AppLanguage.ENGLISH -> "English"
}

private val TAB_UNDERLINE_WIDTH = 24.dp
private val TAB_UNDERLINE_HEIGHT = 3.dp
