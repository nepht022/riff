package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test

/**
 * As mesmas cenas da pista do celular, desenhadas no PC (texturas pelo Skia). A suavização das bordas muda entre
 * Windows e Linux: até 1% dos pixels passa, como no screenshot da tela inicial do PC.
 */
class DesktopStageScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, width: Dp, height: Dp, scene: @Composable () -> Unit) {
        composeRule.setContent { Box(Modifier.size(width, height)) { scene() } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/desktop/$name.png", roborazziOptions = Tolerance)
    }

    @Test
    fun stageWithGemsButtonsFlameAndHud() = capture("Stage", 360.dp, 640.dp) { StageScene() }

    @Test
    fun gemAndButtonCloseUp() = capture("Stage_closeup", 360.dp, 140.dp) { CloseUpScene() }

    @Test
    fun flameKeyframes() = capture("Stage_flame_frames", 360.dp, 240.dp) { FlameFramesScene() }

    @Test
    fun everyBackground() = capture("Stage_backgrounds", 840.dp, 400.dp) { LookSheetScene(EveryBackground) }

    @Test
    fun everyArt() = capture("Stage_art", 840.dp, 400.dp) { LookSheetScene(EveryArt) }

    @Test
    fun everyNoteAndButtonStyle() = capture("Stage_pieces", 360.dp, 640.dp) { PiecesScene() }

    private companion object {
        val Tolerance = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))
    }
}
