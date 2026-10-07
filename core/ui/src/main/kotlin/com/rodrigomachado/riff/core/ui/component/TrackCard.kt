package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.theme.Spacing

@Composable
fun TrackCard(
    title: String,
    artist: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
) {
    val cardModifier = modifier.fillMaxWidth().heightIn(min = 48.dp)
    val content: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    if (onClick != null) {
        Card(onClick = onClick, modifier = cardModifier, content = content)
    } else {
        Card(modifier = cardModifier, content = content)
    }
}
