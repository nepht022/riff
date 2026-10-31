package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

enum class BadgeTone { SUCCESS, WARNING, NEUTRAL, ERROR }

/** Carimbo chapado, cantos retos, texto maiúsculo espaçado. */
@Composable
fun StatusBadge(text: String, tone: BadgeTone, modifier: Modifier = Modifier) {
    val (container, content) = when (tone) {
        BadgeTone.SUCCESS -> RiffColors.StampSuccess to RiffColors.Paper
        BadgeTone.WARNING -> RiffColors.StampWarning to RiffColors.Paper
        BadgeTone.NEUTRAL -> RiffColors.PaperDim to RiffColors.Ink
        BadgeTone.ERROR -> RiffColors.StampError to RiffColors.Paper
    }
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
        color = content,
        modifier = modifier.background(container).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}
