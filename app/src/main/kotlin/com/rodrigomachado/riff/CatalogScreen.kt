package com.rodrigomachado.riff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.ErrorState
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.TrackCard
import com.rodrigomachado.riff.core.ui.theme.LaneColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

/** Vitrine temporária do design system, até as features existirem. */
@Composable
fun CatalogScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Text(stringResource(R.string.catalog_title), style = MaterialTheme.typography.headlineSmall) }
        item {
            TrackCard("Through the Fire and Flames", "DragonForce", onClick = {}, badge = {
                StatusBadge(stringResource(R.string.catalog_badge_matched), BadgeTone.SUCCESS)
            })
        }
        item {
            TrackCard("Bohemian Rhapsody", "Queen", onClick = {}, badge = {
                StatusBadge(stringResource(R.string.catalog_badge_confirm), BadgeTone.WARNING)
            })
        }
        item {
            TrackCard("Garota de Ipanema", "Tom Jobim", badge = {
                StatusBadge(stringResource(R.string.catalog_badge_no_chart), BadgeTone.NEUTRAL)
            })
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                LaneColors.all.forEach { Box(Modifier.size(40.dp).background(it, CircleShape)) }
            }
        }
        item {
            Box(Modifier.height(260.dp)) {
                EmptyState(
                    title = stringResource(R.string.catalog_empty_title),
                    message = stringResource(R.string.catalog_empty_message),
                    actionLabel = stringResource(R.string.catalog_empty_action),
                    onAction = {},
                )
            }
        }
        item {
            Box(Modifier.height(200.dp)) {
                ErrorState(message = stringResource(R.string.catalog_error_message), onRetry = {})
            }
        }
    }
}
