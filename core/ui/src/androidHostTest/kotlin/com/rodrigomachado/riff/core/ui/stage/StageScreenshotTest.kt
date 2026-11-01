package com.rodrigomachado.riff.core.ui.stage

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi")
class StageScreenshotTest {
    @Test
    fun stageWithGemsButtonsFlameAndHud() = captureRoboImage("src/test/screenshots/Stage.png") { StageScene() }

    @Test
    fun gemAndButtonCloseUp() = captureRoboImage("src/test/screenshots/Stage_closeup.png") { CloseUpScene() }

    @Test
    fun flameKeyframes() = captureRoboImage("src/test/screenshots/Stage_flame_frames.png") { FlameFramesScene() }

    @Test
    @Config(qualifiers = "pt-rBR-w840dp-h400dp-xhdpi")
    fun everyBackground() =
        captureRoboImage("src/test/screenshots/Stage_backgrounds.png") { LookSheetScene(EveryBackground) }

    @Test
    @Config(qualifiers = "pt-rBR-w840dp-h400dp-xhdpi")
    fun everyArt() = captureRoboImage("src/test/screenshots/Stage_art.png") { LookSheetScene(EveryArt) }

    /** Uma linha por design: trilha 1 é HOPO, a 3 está perdida e o botão 2 está pressionado. */
    @Test
    fun everyNoteAndButtonStyle() = captureRoboImage("src/test/screenshots/Stage_pieces.png") { PiecesScene() }
}
