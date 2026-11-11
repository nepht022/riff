package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.input.LocalGamepad
import com.rodrigomachado.riff.core.ui.resources.Res
import com.rodrigomachado.riff.core.ui.resources.ui_keys_back
import com.rodrigomachado.riff.core.ui.resources.ui_keys_choose
import com.rodrigomachado.riff.core.ui.resources.ui_keys_move
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * Uma tecla do rodapé do PC: [keys] no teclado ("Enter", "← →"), [pad] no controle ("✕"; nulo = só teclado) e o
 * que ela faz nesta tela ([label]).
 */
data class KeyHint(val keys: String, val pad: String?, val label: String)

private val KeycapBorder = 1.dp

object KeyHintTestTags {
    /** A barra de teclas das telas que a mostram por [PcKeyHints]. */
    const val BAR = "key_hints"
}

/** "← → ↑ ↓ Mover" (as setas andam pelo foco; no controle, o direcional, que não tem símbolo próprio na barra). */
@Composable
fun moveHint(keys: String = "← → ↑ ↓", label: String = stringResource(Res.string.ui_keys_move)) =
    KeyHint(keys, pad = null, label = label)

/** "Enter / ✕ Escolher". */
@Composable
fun chooseHint(label: String = stringResource(Res.string.ui_keys_choose)) = KeyHint("Enter", pad = "✕", label = label)

/** "Esc / ○ Voltar". */
@Composable
fun backHint(label: String = stringResource(Res.string.ui_keys_back)) = KeyHint("Esc", pad = "○", label = label)

/**
 * A barra de teclas só no PC (onde há o controle do PC, [LocalGamepad]), com os símbolos do controle quando há um
 * ligado; no celular, nada.
 */
@Composable
fun PcKeyHints(hints: List<KeyHint>, modifier: Modifier = Modifier) {
    val pad = LocalGamepad.current ?: return
    val connected by pad.connected.collectAsState()
    KeyHintBar(hints, connected, modifier.testTag(KeyHintTestTags.BAR))
}

/**
 * Uma tela inteira com a barra de teclas do PC ([PcKeyHints]) no rodapé: [content] fica com o resto da altura. No
 * celular (sem a barra), o mesmo que uma caixa do tamanho da tela.
 */
@Composable
fun WithPcKeyHints(hints: List<KeyHint>, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), content = content)
        PcKeyHints(hints)
    }
}

/**
 * O rodapé fixo do PC com as teclas da tela atual ("Enter / ✕ Escolher"). Os símbolos do controle só aparecem com um
 * ligado ([padConnected], do `Gamepad.connected` do PC); sem controle, só o teclado.
 */
@Composable
fun KeyHintBar(hints: List<KeyHint>, padConnected: Boolean, modifier: Modifier = Modifier) {
    FlowRow(
        modifier
            .fillMaxWidth()
            .background(RiffColors.Ink)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        hints.forEach { hint -> Hint(hint, padConnected) }
    }
}

@Composable
private fun Hint(hint: KeyHint, padConnected: Boolean) {
    // Cada tecla é lida de uma vez pelo leitor de tela: "Enter, ✕, Escolher".
    Row(
        Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Keycap(hint.keys)
        if (padConnected && hint.pad != null) {
            Text("/", style = MaterialTheme.typography.labelMedium, color = RiffColors.PaperDim)
            Keycap(hint.pad)
        }
        Text(
            hint.label,
            style = MaterialTheme.typography.bodyMedium,
            color = RiffColors.Paper,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

/** A tecla desenhada como tecla: contorno de papel apagado, texto em papel. */
@Composable
private fun Keycap(text: String) {
    Box(
        Modifier
            .border(KeycapBorder, RiffColors.PaperDim, MaterialTheme.shapes.small)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = RiffColors.Paper)
    }
}
