package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.FretStyle
import com.rodrigomachado.riff.core.model.HighwayArt
import com.rodrigomachado.riff.core.model.HighwayBackground
import com.rodrigomachado.riff.core.model.NoteStyle
import com.rodrigomachado.riff.core.model.TrackLook
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.LaneColors
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.core.ui.theme.Spacing

/** As cenas da pista dos testes de tela, as mesmas no celular e no PC. */

@Composable
fun StageScene() {
    RiffTheme {
        StageBackground(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val cache = StageCache()
                val geometry = cache.update(size, density)
                floatArrayOf(0.6f, 1.6f, 2.6f, 3.6f, 4.6f).copyInto(cache.fretDistances)
                cache.fretCount = 5
                drawHighway(cache)
                val gemRadius = geometry.laneWidthAtStrike * 0.42f
                drawSustainTrail(
                    geometry.laneCenter(3, 1.65f),
                    geometry.laneCenter(3, 3.3f),
                    gemRadius * 0.35f,
                    LaneColors.forLane(3),
                    active = false,
                )
                // Notas igualmente espaçadas no tempo, do fundo para a frente.
                listOf(
                    4 to 4.95f, 0 to 4.4f, 2 to 3.85f, 1 to 3.3f, 3 to 1.65f, 4 to 2.2f, 0 to 2.75f,
                    2 to 1.1f, 1 to 0.55f,
                )
                    .sortedByDescending { it.second }
                    .forEach { (lane, distance) ->
                        drawGem(
                            cache,
                            geometry.laneCenter(lane, distance),
                            gemRadius * geometry.scaleAt(distance),
                            lane,
                            hopo = lane == 1,
                        )
                    }
                for (lane in 0 until 5) {
                    drawFretButton(cache, geometry.laneCenter(lane, 0f), gemRadius, lane, pressed = lane == 2)
                }
                val hit = geometry.laneCenter(2, 0f)
                drawFlame(
                    cache,
                    hit,
                    geometry.laneWidthAtStrike * 0.62f,
                    geometry.laneWidthAtStrike * 1.7f,
                    progress = 0.2f,
                )
            }
            Row(
                Modifier.fillMaxSize().padding(Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                ScoreText(48_300)
                MultiplierStamp(3)
            }
            JudgmentText("PERFEITO!", Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun CloseUpScene() {
    RiffTheme {
        StageBackground {
            Canvas(Modifier.size(360.dp, 140.dp)) {
                val r = 34.dp.toPx()
                val y = size.height / 2
                val cache = StageCache()
                drawGem(cache, Offset(size.width * 0.14f, y), r, lane = 1)
                drawGem(cache, Offset(size.width * 0.38f, y), r, lane = 1, hopo = true)
                drawFretButton(cache, Offset(size.width * 0.62f, y), r, lane = 1, pressed = false)
                drawFretButton(cache, Offset(size.width * 0.86f, y), r, lane = 1, pressed = true)
            }
        }
    }
}

@Composable
fun FlameFramesScene() {
    RiffTheme {
        StageBackground {
            Column {
                Canvas(Modifier.size(360.dp, 200.dp)) {
                    val cache = StageCache()
                    val progresses = listOf(0.05f, 0.15f, 0.4f, 0.8f)
                    progresses.forEachIndexed { i, progress ->
                        val base = Offset(size.width * (i + 0.5f) / progresses.size, size.height * 0.9f)
                        drawFlame(cache, base, 50.dp.toPx(), 140.dp.toPx(), progress)
                    }
                }
                Box(Modifier.padding(Spacing.md))
            }
        }
    }
}

@Composable
fun PiecesScene() {
    RiffTheme {
        StageBackground(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val rows = NoteStyle.entries.size + FretStyle.entries.size
                val rowHeight = size.height / rows
                val r = size.width / 14f
                NoteStyle.entries.forEachIndexed { row, style ->
                    val cache = StageCache().apply { look = TrackLook.DEFAULT.copy(notes = style) }
                    for (lane in 0 until 5) {
                        val center = Offset(size.width * (lane + 1) / 6f, rowHeight * (row + 0.7f))
                        drawGem(cache, center, r, lane, missed = lane == 3, hopo = lane == 1)
                    }
                }
                FretStyle.entries.forEachIndexed { i, style ->
                    val cache = StageCache().apply { look = TrackLook.DEFAULT.copy(frets = style) }
                    val row = NoteStyle.entries.size + i
                    for (lane in 0 until 5) {
                        val center = Offset(size.width * (lane + 1) / 6f, rowHeight * (row + 0.5f))
                        drawFretButton(cache, center, r, lane, pressed = lane == 2)
                    }
                }
            }
        }
    }
}

/** Uma pista por visual, lado a lado, já rolada um pouco (a textura não começa alinhada à linha de acerto). */
@Composable
fun LookSheetScene(looks: List<TrackLook>) {
    RiffTheme {
        StageBackground(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                looks.forEach { look ->
                    Canvas(Modifier.width(120.dp).fillMaxSize()) {
                        val cache = StageCache()
                        cache.look = look
                        cache.scrollDistance = 0.7f
                        cache.update(size, density)
                        drawHighway(cache)
                    }
                }
            }
        }
    }
}

val EveryBackground = HighwayBackground.entries.map { TrackLook.DEFAULT.copy(background = it) }
val EveryArt = HighwayArt.entries.map { TrackLook.DEFAULT.copy(background = HighwayBackground.AMPLIFIER, art = it) }
