package com.rodrigomachado.riff

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.TrackCard
import com.rodrigomachado.riff.core.ui.stage.HighwayGeometry
import com.rodrigomachado.riff.core.ui.stage.drawFlame
import com.rodrigomachado.riff.core.ui.stage.drawFretButton
import com.rodrigomachado.riff.core.ui.stage.drawGem
import com.rodrigomachado.riff.core.ui.stage.drawHighway
import com.rodrigomachado.riff.core.ui.theme.LaneColors
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

/** Vitrine temporária da identidade visual, até as features existirem. */
@Composable
fun CatalogScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { PosterTitle("RIFF") }
        item {
            Text(
                stringResource(R.string.catalog_now_playing),
                style = MaterialTheme.typography.labelMedium,
                color = RiffColors.PaperDim,
            )
        }
        item {
            TrackCard("Through the Fire and Flames", "DragonForce", tilted = true, onClick = {}, badge = {
                StatusBadge(stringResource(R.string.catalog_badge_matched), BadgeTone.SUCCESS)
            })
        }
        item { PosterButton(stringResource(R.string.catalog_play), onClick = {}) }
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
        item { StagePreview() }
        item {
            Box(Modifier.height(280.dp)) {
                EmptyState(
                    title = stringResource(R.string.catalog_empty_title),
                    message = stringResource(R.string.catalog_empty_message),
                    actionLabel = stringResource(R.string.catalog_empty_action),
                    onAction = {},
                )
            }
        }
        item {
            Box(Modifier.height(220.dp)) {
                ErrorState(message = stringResource(R.string.catalog_error_message), onRetry = {})
            }
        }
    }
}

/** Pista estática para conferir as peças no aparelho de verdade. */
@Composable
private fun StagePreview() {
    Canvas(Modifier.fillMaxWidth().height(320.dp)) {
        val geometry = HighwayGeometry(size)
        drawHighway(geometry, fretDistances = listOf(0.6f, 1.6f, 2.6f, 3.6f))
        val radius = geometry.laneWidthAtStrike * 0.42f
        drawGem(geometry.laneCenter(0, 2.2f), radius * geometry.scaleAt(2.2f), LaneColors.forLane(0))
        drawGem(geometry.laneCenter(3, 1.1f), radius * geometry.scaleAt(1.1f), LaneColors.forLane(3), hopo = true)
        for (lane in 0 until 5) {
            drawFretButton(geometry.laneCenter(lane, 0f), radius, LaneColors.forLane(lane), pressed = lane == 1)
        }
        drawFlame(
            geometry.laneCenter(1, 0f),
            geometry.laneWidthAtStrike * 0.62f,
            geometry.laneWidthAtStrike * 1.7f,
            progress = 0.2f,
        )
    }
}
