package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.core.ui.theme.Spacing

enum class BadgeTone { SUCCESS, WARNING, NEUTRAL, ERROR }

@Composable
fun StatusBadge(text: String, tone: BadgeTone, modifier: Modifier = Modifier) {
    val extended = RiffTheme.extendedColors
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        BadgeTone.SUCCESS -> extended.success to extended.onSuccess
        BadgeTone.WARNING -> extended.warning to extended.onWarning
        BadgeTone.NEUTRAL -> scheme.surfaceVariant to scheme.onSurfaceVariant
        BadgeTone.ERROR -> scheme.errorContainer to scheme.onErrorContainer
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}
