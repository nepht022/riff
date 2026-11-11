package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.resources.Res
import com.rodrigomachado.riff.core.ui.resources.ui_back
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/** A altura mínima do toque (≥ 44 dp, como pede o design). */
private val BackRowMinHeight = 44.dp
private val ArrowSize = 20.dp
private val ArrowStroke = 2.5.dp

/**
 * O "Voltar" do topo das telas: seta desenhada e [text] em papel ("Sair da sala" na sala), com 44 dp de altura e o
 * destaque do foco no PC. Sem som próprio: o som de voltar toca na navegação, venha de onde vier a volta.
 */
@Composable
fun BackRow(onBack: () -> Unit, modifier: Modifier = Modifier, text: String = stringResource(Res.string.ui_back)) {
    Row(
        modifier
            .focusHighlight(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onBack)
            .heightIn(min = BackRowMinHeight)
            .padding(horizontal = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackArrow()
        Text(text, style = MaterialTheme.typography.titleMedium, color = RiffColors.Paper)
    }
}

/** Seta para a esquerda: haste e ponta em traço de papel (sem depender de ícones nem de glifo da fonte). */
@Composable
private fun BackArrow() {
    Canvas(Modifier.size(ArrowSize)) {
        val stroke = ArrowStroke.toPx()
        val middle = size.height / 2
        val left = stroke
        val head = size.width * 0.4f
        val path = Path().apply {
            moveTo(size.width - stroke, middle)
            lineTo(left, middle)
            moveTo(left + head, middle - head)
            lineTo(left, middle)
            lineTo(left + head, middle + head)
        }
        drawPath(
            path,
            RiffColors.Paper,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
