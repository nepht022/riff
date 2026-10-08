package com.rodrigomachado.riff.feature.gameplay

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameStageBackTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var pauses = 0

    private fun show(isPaused: Boolean) {
        val session = GameSession(
            listOf(note(60_000)),
            FakeSongPlayer(),
            FakeSfxPlayer(),
            FakeTime(),
            audioOffsetMs = 0,
        )
        val state = GameplayUiState.Ready("Música", "Banda", Difficulty.EXPERT, session, Settings.DEFAULT, isPaused)
        composeRule.setContent {
            RiffTheme {
                GameStage(
                    state,
                    reducedMotion = true,
                    onPause = { pauses++ },
                    onResume = {},
                    onExit = {},
                    onSessionFinished = {},
                    animate = false,
                )
            }
        }
    }

    private fun pressBack() = composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun backDuringTheSongPausesInsteadOfLeaving() {
        show(isPaused = false)

        pressBack()

        assertEquals(1, pauses)
    }

    @Test
    fun backOnThePauseScreenIsNotIntercepted() {
        show(isPaused = true)

        pressBack()

        assertEquals(0, pauses)
    }
}
