package com.rodrigomachado.riff.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.BackRow
import com.rodrigomachado.riff.core.ui.component.KeyHint
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.SeparationIndicator
import com.rodrigomachado.riff.core.ui.component.backHint
import com.rodrigomachado.riff.core.ui.component.chooseHint
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.moveHint
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.input.menuKeysEnabled
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.settings.resources.Res
import com.rodrigomachado.riff.feature.settings.resources.settings_back
import com.rodrigomachado.riff.feature.settings.resources.settings_keys_change
import com.rodrigomachado.riff.feature.settings.resources.settings_keys_tabs
import com.rodrigomachado.riff.feature.settings.resources.settings_licenses
import com.rodrigomachado.riff.feature.settings.resources.settings_title
import org.jetbrains.compose.resources.stringResource

/** A partir desta largura (o PC), as abas vão para uma coluna à esquerda, como nas outras telas do design novo. */
internal val WIDE_MIN_WIDTH = 800.dp

/** As linhas de opção estão na janela larga do PC: blocos maiores, com fundo, título de 24 sp e descrição de 16 sp. */
internal val LocalWideSettings = staticCompositionLocalOf { false }

/** O título de uma linha de opção (24 sp nos blocos do PC). */
@Composable
internal fun rowTitleStyle(): TextStyle = MaterialTheme.typography.titleMedium.let {
    if (LocalWideSettings.current) it.copy(fontSize = WIDE_TITLE, lineHeight = WIDE_TITLE_LINE) else it
}

/** A descrição de uma linha de opção (16 sp nos blocos do PC). */
@Composable
internal fun rowHintStyle(): TextStyle = MaterialTheme.typography.bodySmall.let {
    if (LocalWideSettings.current) it.copy(fontSize = WIDE_HINT, lineHeight = WIDE_HINT_LINE) else it
}

/** O fundo de bloco das linhas no PC largo ([wide]); no celular, a linha fica como era. */
internal fun Modifier.settingBlock(wide: Boolean): Modifier =
    if (wide) background(RiffColors.Ink, BlockShape).padding(horizontal = Spacing.md) else this

/**
 * As Configurações na janela larga do PC: à esquerda a coluna das abas (fundo mais escuro, com Voltar e o título no
 * topo, a aba aberta em vermelho e "Licenças e privacidade" no pé); à direita as opções da aba em blocos maiores.
 * ↑/↓ andam nas abas e nas opções; → entra nas opções, ← volta às abas (fora de um controle deslizante, onde ←/→
 * mudam o valor).
 */
@Composable
internal fun WideSettings(
    tabs: List<SettingsTab>,
    tab: SettingsTab,
    onTab: (SettingsTab) -> Unit,
    onBack: () -> Unit,
    onLicenses: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    // → numa aba leva à primeira opção (a mais perto, pelas setas, podia ser uma do meio).
    val options = remember { FocusRequester() }
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .testTag(SettingsTestTags.TABS_COLUMN)
                .width(TABS_COLUMN_WIDTH)
                .fillMaxHeight()
                .background(TABS_COLUMN_SHADE)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            BackRow(onBack = onBack, text = stringResource(Res.string.settings_back))
            PosterTitle(stringResource(Res.string.settings_title), style = MaterialTheme.typography.headlineMedium)
            // As abas tomam a altura que sobra (e rolam numa janela baixa): as licenças ficam no pé.
            TabsColumn(tabs, tab, onTab, options, Modifier.weight(1f))
            onLicenses?.let { LicensesLink(it) }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = Spacing.lg)) {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                Spacer(Modifier.weight(1f))
                SeparationIndicator()
            }
            // Cada aba começa do topo.
            key(tab) {
                Column(
                    Modifier
                        .testTag(SettingsTestTags.OPTIONS)
                        .focusRequester(options)
                        .focusGroup()
                        .weight(1f)
                        .widthIn(max = OPTIONS_MAX_WIDTH)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    CompositionLocalProvider(LocalWideSettings provides true) { content() }
                }
            }
        }
    }
}

/** Nos menus do PC, → a partir deste item vai para [target]; no celular, nada muda. */
private fun Modifier.pcArrowRight(target: FocusRequester): Modifier =
    if (menuKeysEnabled) focusProperties { right = target } else this

/** As abas uma embaixo da outra; a aberta em vermelho. O TalkBack diz "aba 2 de 6" (selectableGroup). */
@Composable
private fun TabsColumn(
    tabs: List<SettingsTab>,
    selected: SettingsTab,
    onSelect: (SettingsTab) -> Unit,
    options: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup().padding(top = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        for (tab in tabs) {
            val isSelected = tab == selected
            Box(
                Modifier
                    .fillMaxWidth()
                    .pcArrowRight(options)
                    .focusHighlight(BlockShape)
                    .background(if (isSelected) RiffColors.TourRed else Color.Transparent, BlockShape)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = withMenuSound(MenuSound.NAVIGATE) { onSelect(tab) },
                    )
                    .heightIn(min = TAB_MIN_HEIGHT)
                    .padding(horizontal = Spacing.md),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    tabTitle(tab),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = TAB_TEXT),
                    color = if (isSelected) RiffColors.Paper else RiffColors.PaperDim,
                )
            }
        }
    }
}

/** "Licenças e privacidade" no pé da coluna das abas. */
@Composable
private fun LicensesLink(onLicenses: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .focusHighlight(BlockShape)
            .clickable(role = Role.Button, onClick = withMenuSound(MenuSound.NAVIGATE, onLicenses))
            .heightIn(min = TAB_MIN_HEIGHT)
            .padding(horizontal = Spacing.md),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            stringResource(Res.string.settings_licenses),
            style = MaterialTheme.typography.bodyLarge,
            color = RiffColors.PaperDim,
        )
    }
}

/** As teclas das Configurações no PC largo. */
@Composable
internal fun wideSettingsKeys(): List<KeyHint> = listOf(
    moveHint("↑ ↓", stringResource(Res.string.settings_keys_tabs)),
    moveHint("← →", stringResource(Res.string.settings_keys_change)),
    chooseHint(),
    backHint(),
)

/** As teclas das Configurações na janela estreita do PC (as abas numa barra, como no celular). */
@Composable
internal fun phoneSettingsKeys(): List<KeyHint> = listOf(moveHint(), chooseHint(), backHint())

private val BlockShape = RoundedCornerShape(4.dp)

/** A coluna das abas escurece o palco por trás (mais escura que o fundo das opções). */
private val TABS_COLUMN_SHADE = Color.Black.copy(alpha = 0.45f)
private val TABS_COLUMN_WIDTH = 260.dp
private val OPTIONS_MAX_WIDTH = 760.dp
private val TAB_MIN_HEIGHT = 48.dp
private val TAB_TEXT = 20.sp
private val WIDE_TITLE = 24.sp
private val WIDE_TITLE_LINE = 30.sp
private val WIDE_HINT = 16.sp
private val WIDE_HINT_LINE = 22.sp
