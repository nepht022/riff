package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.rodrigomachado.riff.core.game.NoteTimeline
import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.model.Note
import com.rodrigomachado.riff.core.ui.stage.HighwayGeometry
import com.rodrigomachado.riff.core.ui.stage.drawFlame
import com.rodrigomachado.riff.core.ui.stage.drawFretButton
import com.rodrigomachado.riff.core.ui.stage.drawGem
import com.rodrigomachado.riff.core.ui.stage.drawHighway
import com.rodrigomachado.riff.core.ui.stage.drawSustainTrail
import com.rodrigomachado.riff.core.ui.theme.LaneColors
import kotlin.math.min

internal const val BASE_MS_PER_DISTANCE = 450f
private const val GEM_RADIUS_FRACTION = 0.42f
private const val TRAIL_WIDTH_FRACTION = 0.35f
private const val MISSED_ALPHA = 0.4f
private const val FRET_SPACING_MS = 500L
private const val FLAME_WIDTH_FRACTION = 0.62f
private const val FLAME_HEIGHT_FRACTION = 1.7f

/** Trilha na tela para a trilha lógica [lane] (canhoto espelha). Vale nos dois sentidos. */
internal fun screenLane(lane: Int, leftHanded: Boolean): Int = if (leftHanded) Note.LANE_COUNT - 1 - lane else lane

/** Desenha a pista no instante atual da [session]. */
internal fun DrawScope.drawStage(session: GameSession, msPerUnit: Float, leftHanded: Boolean, reducedMotion: Boolean) {
    val geometry = HighwayGeometry(size)
    val t = session.songTimeMs
    val visibleMs = (geometry.visibleDistance * msPerUnit).toLong()
    drawHighway(geometry, fretDistances(t, visibleMs, msPerUnit))
    val radius = geometry.laneWidthAtStrike * GEM_RADIUS_FRACTION
    val notes = session.engine.notes
    val range = NoteTimeline.visibleRange(notes, t - session.lookBehindMs, t + visibleMs)
    // Do fundo para a frente, para as notas mais próximas ficarem por cima.
    for (i in range.last downTo range.first) {
        drawNote(geometry, session, i, (notes[i].timeMs - t) / msPerUnit, msPerUnit, radius, leftHanded)
    }
    drawTargetsAndFlames(geometry, session, radius, leftHanded, reducedMotion)
}

private fun DrawScope.drawNote(
    geometry: HighwayGeometry,
    session: GameSession,
    index: Int,
    head: Float,
    msPerUnit: Float,
    radius: Float,
    leftHanded: Boolean,
) {
    val note = session.engine.notes[index]
    val judgment = session.engine.judgmentOf(index)
    val lane = screenLane(note.lane, leftHanded)
    val color = LaneColors.forLane(note.lane)
    val wasHit = judgment == Judgment.PERFECT || judgment == Judgment.GOOD
    if (note.sustainMs > 0) {
        val tail = min(head + note.sustainMs / msPerUnit, geometry.visibleDistance)
        val start = if (wasHit) maxOf(head, 0f) else maxOf(head, geometry.nearDistance)
        if (tail > start) {
            drawSustainTrail(
                geometry.laneCenter(lane, start),
                geometry.laneCenter(lane, tail),
                radius * TRAIL_WIDTH_FRACTION * geometry.scaleAt(start),
                color,
                active = wasHit && session.isPressed(note.lane),
            )
        }
    }
    if (wasHit || head !in geometry.nearDistance..geometry.visibleDistance) return
    val gemColor = if (judgment == Judgment.MISS) color.copy(alpha = MISSED_ALPHA) else color
    drawGem(geometry.laneCenter(lane, head), radius * geometry.scaleAt(head), gemColor, note.isHopo)
}

private fun DrawScope.drawTargetsAndFlames(
    geometry: HighwayGeometry,
    session: GameSession,
    radius: Float,
    leftHanded: Boolean,
    reducedMotion: Boolean,
) {
    for (lane in 0 until Note.LANE_COUNT) {
        val center = geometry.laneCenter(screenLane(lane, leftHanded), 0f)
        drawFretButton(center, radius, LaneColors.forLane(lane), session.isPressed(lane))
    }
    if (reducedMotion) return
    for (lane in 0 until Note.LANE_COUNT) {
        val progress = session.flameProgress(lane)
        if (progress !in 0f..1f) continue
        drawFlame(
            geometry.laneCenter(screenLane(lane, leftHanded), 0f),
            geometry.laneWidthAtStrike * FLAME_WIDTH_FRACTION,
            geometry.laneWidthAtStrike * FLAME_HEIGHT_FRACTION,
            progress,
        )
    }
}

/** Trastes a cada [FRET_SPACING_MS] de música, rolando junto com as notas. */
private fun fretDistances(t: Long, visibleMs: Long, msPerUnit: Float): List<Float> {
    val first = (t / FRET_SPACING_MS + 1) * FRET_SPACING_MS
    return generateSequence(first) { it + FRET_SPACING_MS }
        .takeWhile { it - t <= visibleMs }
        .map { (it - t) / msPerUnit }
        .toList()
}
