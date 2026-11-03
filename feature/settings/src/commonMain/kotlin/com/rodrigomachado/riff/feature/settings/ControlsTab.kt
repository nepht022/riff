package com.rodrigomachado.riff.feature.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import com.rodrigomachado.riff.core.model.ControlAction
import com.rodrigomachado.riff.core.model.Controls
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.input.LocalGamepad
import com.rodrigomachado.riff.core.ui.input.PAD_MENU_BACK
import com.rodrigomachado.riff.core.ui.input.TakePadFromMenus
import com.rodrigomachado.riff.core.ui.input.menuKeys
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.settings.resources.Res
import com.rodrigomachado.riff.feature.settings.resources.settings_action_fret_1
import com.rodrigomachado.riff.feature.settings.resources.settings_action_fret_2
import com.rodrigomachado.riff.feature.settings.resources.settings_action_fret_3
import com.rodrigomachado.riff.feature.settings.resources.settings_action_fret_4
import com.rodrigomachado.riff.feature.settings.resources.settings_action_fret_5
import com.rodrigomachado.riff.feature.settings.resources.settings_action_pause
import com.rodrigomachado.riff.feature.settings.resources.settings_action_strum_down
import com.rodrigomachado.riff.feature.settings.resources.settings_action_strum_up
import com.rodrigomachado.riff.feature.settings.resources.settings_controls_button
import com.rodrigomachado.riff.feature.settings.resources.settings_controls_cancel
import com.rodrigomachado.riff.feature.settings.resources.settings_controls_defaults
import com.rodrigomachado.riff.feature.settings.resources.settings_controls_dpad
import com.rodrigomachado.riff.feature.settings.resources.settings_controls_escape
import com.rodrigomachado.riff.feature.settings.resources.settings_controls_keyboard
import com.rodrigomachado.riff.feature.settings.resources.settings_controls_no_pad
import com.rodrigomachado.riff.feature.settings.resources.settings_controls_pad
import com.rodrigomachado.riff.feature.settings.resources.settings_controls_press_button
import com.rodrigomachado.riff.feature.settings.resources.settings_controls_press_key
import com.rodrigomachado.riff.feature.settings.resources.settings_controls_strum
import com.rodrigomachado.riff.feature.settings.resources.settings_controls_strum_hint
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource

object ControlsTestTags {
    const val STRUM = "controls_strum"
    const val CHOOSING = "controls_choosing"
}

/** A escolha aberta: a tecla ([keyboard]) ou o botão do controle de [action]. */
private data class Choosing(val action: ControlAction, val keyboard: Boolean)

/**
 * Controles (só no PC): tocar com ou sem palheta e, para cada ação, a tecla e o botão do controle. Tocar numa linha
 * abre o diálogo que espera a tecla (ou o botão); escolher uma já usada por outra ação troca as duas (o modelo
 * troca). Sem controle ligado, as linhas do controle continuam escolhíveis (o botão chega quando ele ligar).
 */
@Composable
internal fun ControlsTab(
    controls: Controls,
    padConnected: Boolean,
    onStrum: (Boolean) -> Unit,
    onKey: (ControlAction, Long) -> Unit,
    onPad: (ControlAction, String) -> Unit,
    onDefaults: () -> Unit,
) {
    var choosing by remember { mutableStateOf<Choosing?>(null) }
    SwitchRow(
        title = stringResource(Res.string.settings_controls_strum),
        hint = stringResource(Res.string.settings_controls_strum_hint),
        checked = controls.strum,
        onChange = onStrum,
        tag = ControlsTestTags.STRUM,
    )
    SectionTitle(stringResource(Res.string.settings_controls_keyboard))
    for (action in ControlAction.entries) {
        val key = controls.keys[action]
        ChoiceRow(actionName(action), key?.let(::keyName).orEmpty()) { choosing = Choosing(action, keyboard = true) }
    }
    SectionTitle(
        stringResource(Res.string.settings_controls_pad),
        note = if (padConnected) null else stringResource(Res.string.settings_controls_no_pad),
    )
    for (action in ControlAction.entries) {
        val button = controls.pad[action]
        ChoiceRow(actionName(action), button?.let { padName(it) }.orEmpty()) {
            choosing = Choosing(action, keyboard = false)
        }
    }
    TextButton(
        onClick = withMenuSound(MenuSound.NAVIGATE, onDefaults),
        modifier = Modifier.focusHighlight(CircleShape),
    ) {
        Text(stringResource(Res.string.settings_controls_defaults), color = RiffColors.Paper)
    }
    choosing?.let { choice ->
        ChooseDialog(
            choice,
            onChosenKey = { onKey(choice.action, it) },
            onChosenPad = { onPad(choice.action, it) },
            onClose = { choosing = null },
        )
    }
}

@Composable
private fun SectionTitle(title: String, note: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = RiffColors.TourRed)
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RiffColors.PaperDim) }
    }
}

/**
 * Espera a tecla (ou o botão do controle) para a ação. Esc cancela e não pode ser escolhida (pausa sempre). Toda
 * tecla fica no diálogo (Enter, Tab e as setas não andam nem clicam nada atrás nem nele); o diálogo do teclado não
 * ouve o controle e o do controle não ouve as teclas. Com a janela sem foco (o controle é lido mesmo assim), nada é
 * escolhido.
 */
@Composable
private fun ChooseDialog(
    choice: Choosing,
    onChosenKey: (Long) -> Unit,
    onChosenPad: (String) -> Unit,
    onClose: () -> Unit,
) {
    val window = LocalWindowInfo.current
    val gamepad = LocalGamepad.current
    val chosenPad by rememberUpdatedState(onChosenPad)
    val close by rememberUpdatedState(onClose)
    // Uma escolha só por diálogo: dois botões na mesma leitura (ou duas teclas antes de a tela refazer) não trocam
    // uma terceira ação em cascata.
    var chosen by remember(choice) { mutableStateOf(false) }
    // No PC o controle não anda nos menus atrás nem neste diálogo: no do botão, todo botão é escolhível (o ○ também;
    // cancela-se com o Esc); no da tecla, só o ○ (vira o Esc, que cancela) — o direcional e o ✕ não viram teclas
    // escolhidas.
    TakePadFromMenus { !choice.keyboard || it != PAD_MENU_BACK }
    if (!choice.keyboard && gamepad != null) {
        LaunchedEffect(choice, gamepad) {
            val button = gamepad.presses.first { window.isWindowFocused }
            chosen = true
            chosenPad(button)
            close()
        }
    }
    val focus = remember { FocusRequester() }
    val name = actionName(choice.action)
    val title =
        if (choice.keyboard) Res.string.settings_controls_press_key else Res.string.settings_controls_press_button
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(title, name)) },
        text = { Text(stringResource(Res.string.settings_controls_escape)) },
        confirmButton = {
            TextButton(
                onClick = withMenuSound(MenuSound.BACK, onClose),
                modifier = Modifier.focusHighlight(CircleShape),
            ) {
                Text(stringResource(Res.string.settings_controls_cancel))
            }
        },
        // No PC o diálogo é o de cima nas teclas dos menus (a tela de trás não lhe toma o foco); as teclas, todas dele.
        modifier = Modifier
            .menuKeys(onBack = null)
            .testTag(ControlsTestTags.CHOOSING)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) {
                    when {
                        it.key == Key.Escape -> onClose()

                        choice.keyboard && window.isWindowFocused && !chosen -> {
                            chosen = true
                            onChosenKey(it.key.keyCode)
                            onClose()
                        }
                    }
                }
                true
            },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun actionName(action: ControlAction): String = stringResource(
    when (action) {
        ControlAction.FRET_1 -> Res.string.settings_action_fret_1
        ControlAction.FRET_2 -> Res.string.settings_action_fret_2
        ControlAction.FRET_3 -> Res.string.settings_action_fret_3
        ControlAction.FRET_4 -> Res.string.settings_action_fret_4
        ControlAction.FRET_5 -> Res.string.settings_action_fret_5
        ControlAction.STRUM_UP -> Res.string.settings_action_strum_up
        ControlAction.STRUM_DOWN -> Res.string.settings_action_strum_down
        ControlAction.PAUSE -> Res.string.settings_action_pause
    },
)

/**
 * O nome de um botão gravado: `m:<índice>` pelo símbolo do PS3 no layout de controle do GLFW (A, B, X, Y, LB, RB,
 * Back, Start, Guide, L3, R3 e o direcional ↑ → ↓ ←), `r:<índice>` como "Botão N" e `h:up`/`h:down` como o
 * direcional; outro texto, como está.
 */
@Composable
internal fun padName(button: String): String {
    val kind = button.substringBefore(':')
    val index = button.substringAfter(':').toIntOrNull()
    return when {
        button == "h:up" -> dpadName("↑") ?: button
        button == "h:down" -> dpadName("↓") ?: button
        index == null -> button
        kind == "r" -> stringResource(Res.string.settings_controls_button, index)
        kind == "m" -> PS3_BUTTONS.getOrNull(index) ?: dpadName(PS3_DPAD.getOrNull(index - PS3_BUTTONS.size)) ?: button
        else -> button
    }
}

@Composable
private fun dpadName(arrow: String?): String? = arrow?.let { stringResource(Res.string.settings_controls_dpad, it) }

private val PS3_BUTTONS = listOf("✕", "○", "□", "△", "L1", "R1", "Select", "Start", "PS", "L3", "R3")
private val PS3_DPAD = listOf("↑", "→", "↓", "←")
