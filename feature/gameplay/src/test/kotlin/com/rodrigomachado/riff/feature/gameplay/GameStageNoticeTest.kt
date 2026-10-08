package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class GameStageNoticeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playingWithoutTheSeparateGuitarIsAnnouncedAtTheStart() {
        val session = GameSession(
            listOf(note(60_000)),
            FakeSongPlayer(),
            FakeSfxPlayer(),
            FakeTime(),
            audioOffsetMs = 0,
        )
        session.start()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            RiffTheme {
                GameStage(
                    GameplayUiState.Ready(
                        "Música",
                        "Banda",
                        Difficulty.EXPERT,
                        session,
                        Settings.DEFAULT,
                        withoutSeparateGuitar = true,
                    ),
                    reducedMotion = true,
                    onPause = {},
                    onResume = {},
                    onExit = {},
                    onSessionFinished = {},
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithText("Tocando sem a guitarra separada").assertExists()
    }
}
