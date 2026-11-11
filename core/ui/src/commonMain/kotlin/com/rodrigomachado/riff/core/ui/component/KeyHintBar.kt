package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

/**
 * Uma tecla do rodapé do PC: [keys] no teclado ("Enter", "← →"), [pad] no controle ("✕"; nulo = só teclado) e o
 * que ela faz nesta tela ([label]).
 */
data class KeyHint(val keys: String, val pad: String?, val label: String)

private val KeycapBorder = 1.dp

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
