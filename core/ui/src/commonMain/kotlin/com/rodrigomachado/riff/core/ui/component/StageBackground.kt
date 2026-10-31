package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.theme.RiffColors

private const val HATCH_ALPHA = 0.025f

/** Fundo de todas as telas: preto de palco com hachura diagonal sutil. */
@Composable
fun StageBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(RiffColors.StageBlack).drawBehind { drawHatch() }, content = content)
}

private fun DrawScope.drawHatch() {
    val step = 6.dp.toPx()
    val stroke = 1.dp.toPx()
    val color = RiffColors.Paper.copy(alpha = HATCH_ALPHA)
    var x = -size.height
    while (x < size.width) {
        drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), stroke)
        x += step
    }
}
