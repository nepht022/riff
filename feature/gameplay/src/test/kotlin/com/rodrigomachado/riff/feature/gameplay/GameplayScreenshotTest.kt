package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi")
class GameplayScreenshotTest {
    @Test
    fun stageMidSong() {
        val time = FakeTime()
        val player = FakeSongPlayer().apply { positionMs = 4_000 }
        val notes = listOf(
            Note(4_000, 2, 0, false),
            Note(4_450, 0, 0, false),
            Note(4_900, 1, 0, true),
            Note(5_350, 3, 900, false),
            Note(5_800, 4, 0, false),
            Note(6_250, 2, 0, false),
            Note(6_700, 0, 0, false),
        )
        val session = GameSession(notes, player, FakeSfxPlayer(), time, audioOffsetMs = 0).also {
            it.start()
            it.tick()
            it.press(2) // acerta a nota amarela agora: chama e "PERFEITO!"
        }
        time.now = 40
        val state = GameplayUiState.Ready("Música", "Banda", Difficulty.EXPERT, session, Settings.DEFAULT)
        captureRoboImage("src/test/screenshots/Gameplay_stage.png") {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    GameStage(
                        state,
                        reducedMotion = false,
                        onPause = {},
                        onResume = {},
                        onExit = {},
                        onSessionFinished = {},
                        animate = false,
                    )
                }
            }
        }
    }
}
