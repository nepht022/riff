package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

private val CardShadow = 4.dp
private const val TILT_DEGREES = -1.5f

/** Cartão de música em papel envelhecido com sombra dura. Use [tilted] só no cartão em evidência, não em listas. */
@Composable
fun TrackCard(
    title: String,
    artist: String,
    modifier: Modifier = Modifier,
    tilted: Boolean = false,
    onClick: (() -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .then(if (tilted) Modifier.rotate(TILT_DEGREES) else Modifier)
            .padding(end = CardShadow, bottom = CardShadow),
    ) {
        Box(Modifier.matchParentSize().offset(CardShadow, CardShadow).background(RiffColors.Ink))
        Row(
            Modifier
                .fillMaxWidth()
                .background(RiffColors.Paper, MaterialTheme.shapes.small)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .heightIn(min = 48.dp)
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = RiffColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RiffColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (badge != null) {
                Spacer(Modifier.width(Spacing.sm))
                badge()
            }
        }
    }
}
