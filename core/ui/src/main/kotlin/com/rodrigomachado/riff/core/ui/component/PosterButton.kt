package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

private val ShadowOffset = 5.dp
private val PressDepth = 3.dp
private const val TILT_DEGREES = -1f
private const val DISABLED_ALPHA = 0.45f

/** Botão principal de pôster: vermelho, texto em papel, sombra dura que some ao pressionar. */
@Composable
fun PosterButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shift = if (pressed) PressDepth else 0.dp
    Box(
        modifier
            .rotate(TILT_DEGREES)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(end = ShadowOffset, bottom = ShadowOffset),
    ) {
        if (!pressed) {
            Box(Modifier.matchParentSize().offset(ShadowOffset, ShadowOffset).background(RiffColors.Ink))
        }
        Box(
            Modifier
                .offset(shift, shift)
                .background(RiffColors.TourRed)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.titleLarge, color = RiffColors.Paper)
        }
    }
}
