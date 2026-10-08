package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.LaneColors
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.core.ui.theme.Spacing
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi")
class StageScreenshotTest {
    private val strikeDepth = 0.86f

    @Test
    fun stageWithGemsButtonsFlameAndHud() {
        captureRoboImage("src/test/screenshots/Stage.png") {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize()) {
                        val geometry = HighwayGeometry(size)
                        drawHighway(geometry, fretDepths = listOf(0.12f, 0.34f, 0.56f, 0.78f))
                        val baseRadius = size.width / 5 * 0.36f
                        val trailStart = geometry.laneCenter(3, 0.55f)
                        val trailEnd = geometry.laneCenter(3, 0.25f)
                        drawSustainTrail(
                            trailStart,
                            trailEnd,
                            baseRadius * 0.35f,
                            LaneColors.forLane(3),
                            active = false,
                        )
                        listOf(0 to 0.2f, 2 to 0.35f, 1 to 0.5f, 3 to 0.55f, 4 to 0.7f).forEach { (lane, depth) ->
                            val r = baseRadius * geometry.scaleAt(depth)
                            drawGem(geometry.laneCenter(lane, depth), r, LaneColors.forLane(lane), hopo = lane == 1)
                        }
                        for (lane in 0 until 5) {
                            drawFretButton(
                                geometry.laneCenter(lane, strikeDepth),
                                baseRadius,
                                LaneColors.forLane(lane),
                                pressed = lane == 2,
                            )
                        }
                        val hit = geometry.laneCenter(2, strikeDepth)
                        drawFlame(hit, baseRadius * 1.6f, baseRadius * 4f, progress = 0.2f)
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
    }

    @Test
    fun gemAndButtonCloseUp() {
        captureRoboImage("src/test/screenshots/Stage_closeup.png") {
            RiffTheme {
                StageBackground {
                    Canvas(Modifier.size(360.dp, 140.dp)) {
                        val r = 34.dp.toPx()
                        val y = size.height / 2
                        val red = LaneColors.forLane(1)
                        drawGem(Offset(size.width * 0.14f, y), r, red)
                        drawGem(Offset(size.width * 0.38f, y), r, red, hopo = true)
                        drawFretButton(Offset(size.width * 0.62f, y), r, red, pressed = false)
                        drawFretButton(Offset(size.width * 0.86f, y), r, red, pressed = true)
                    }
                }
            }
        }
    }

    @Test
    fun flameKeyframes() {
        captureRoboImage("src/test/screenshots/Stage_flame_frames.png") {
            RiffTheme {
                StageBackground {
                    Column {
                        Canvas(Modifier.size(360.dp, 200.dp)) {
                            val progresses = listOf(0.05f, 0.15f, 0.4f, 0.8f)
                            progresses.forEachIndexed { i, progress ->
                                val base = Offset(size.width * (i + 0.5f) / progresses.size, size.height * 0.9f)
                                drawFlame(base, 50.dp.toPx(), 140.dp.toPx(), progress)
                            }
                        }
                        Box(Modifier.padding(Spacing.md))
                    }
                }
            }
        }
    }
}
