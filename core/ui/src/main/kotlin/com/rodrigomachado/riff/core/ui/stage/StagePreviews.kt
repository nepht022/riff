package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.rodrigomachado.riff.core.model.FretStyle
import com.rodrigomachado.riff.core.model.NoteStyle
import com.rodrigomachado.riff.core.model.TrackLook

/** Miniatura da pista com o visual dado: as Configurações mostram o que vai aparecer no jogo. */
@Composable
fun HighwayPreview(look: TrackLook, modifier: Modifier = Modifier) {
    val cache = remember(look) {
        StageCache().also {
            it.look = look
            it.scrollDistance = PREVIEW_SCROLL
        }
    }
    Canvas(modifier) {
        val geometry = cache.update(size, density)
        drawHighway(cache)
        val r = geometry.laneWidthAtStrike * GEM_SIZE
        for ((lane, d) in PREVIEW_NOTES) drawGem(cache, geometry.laneCenter(lane, d), r * geometry.scaleAt(d), lane)
        for (lane in 0 until geometry.laneCount) {
            drawFretButton(cache, geometry.laneCenter(lane, 0f), r, lane, pressed = false)
        }
    }
}

/** As cinco notas lado a lado no design dado. */
@Composable
fun NotesPreview(style: NoteStyle, modifier: Modifier = Modifier) {
    val cache = remember(style) { StageCache().also { it.look = TrackLook.DEFAULT.copy(notes = style) } }
    Canvas(modifier) {
        val r = minOf(size.height * 0.32f, size.width / 12f)
        for (lane in 0 until LANES) {
            drawGem(cache, Offset(size.width * (lane + 0.5f) / LANES, size.height * 0.66f), r, lane)
        }
    }
}

/** Os cinco botões lado a lado no design dado. */
@Composable
fun FretsPreview(style: FretStyle, modifier: Modifier = Modifier) {
    val cache = remember(style) { StageCache().also { it.look = TrackLook.DEFAULT.copy(frets = style) } }
    Canvas(modifier) {
        val r = minOf(size.height * 0.45f, size.width / 11f)
        for (lane in 0 until LANES) {
            drawFretButton(
                cache,
                Offset(size.width * (lane + 0.5f) / LANES, size.height / 2f),
                r,
                lane,
                pressed = false,
            )
        }
    }
}

private const val LANES = 5
private const val GEM_SIZE = 0.42f
private const val PREVIEW_SCROLL = 0.7f
private val PREVIEW_NOTES = listOf(1 to 2.4f, 3 to 1.4f)
