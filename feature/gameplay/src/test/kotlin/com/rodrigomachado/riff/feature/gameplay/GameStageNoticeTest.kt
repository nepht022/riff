package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun thePauseMenuAdjustsTheSyncOfThisSong() {
        val adjustments = mutableListOf<Int>()
        val session = GameSession(
            listOf(note(60_000)),
            FakeSongPlayer(),
            FakeSfxPlayer(),
            FakeTime(),
            audioOffsetMs = 0,
        )
        composeRule.setContent {
            RiffTheme {
                GameStage(
                    GameplayUiState.Ready(
                        "Música",
                        "Banda",
                        Difficulty.EXPERT,
                        session,
                        Settings.DEFAULT,
                        isPaused = true,
                        songOffsetMs = 20,
                    ),
                    reducedMotion = true,
                    onPause = {},
                    onResume = {},
                    onExit = {},
                    onSessionFinished = {},
                    onAdjustOffset = { adjustments += it },
                    animate = false,
                )
            }
        }

        composeRule.onNodeWithText("Sincronia desta música: +20 ms").assertExists()
        composeRule.onNodeWithText("−10 ms").performClick()
        composeRule.onNodeWithText("+10 ms").performClick()
        composeRule.onNodeWithText("−1 s").performClick()
        composeRule.onNodeWithText("+1 s").performClick()

        assertEquals(listOf(-10, 10, -1_000, 1_000), adjustments)
    }

    @Test
    fun pauseAndExitHaveTagsForTheAutomatedJourneys() {
        var paused = false
        var exited = false
        val session = GameSession(listOf(note(60_000)), FakeSongPlayer(), FakeSfxPlayer(), FakeTime(), audioOffsetMs = 0)
        var isPaused by mutableStateOf(false)
        composeRule.setContent {
            RiffTheme {
                GameStage(
                    GameplayUiState.Ready(
                        "Música",
                        "Banda",
                        Difficulty.EXPERT,
                        session,
                        Settings.DEFAULT,
                        isPaused = isPaused,
                    ),
                    reducedMotion = true,
                    onPause = {
                        paused = true
                        isPaused = true
                    },
                    onResume = {},
                    onExit = { exited = true },
                    onSessionFinished = {},
                    animate = false,
                )
            }
        }

        composeRule.onNodeWithTag(GameplayTestTags.PAUSE).performClick()
        composeRule.onNodeWithTag(GameplayTestTags.EXIT).performClick()

        assertTrue(paused)
        assertTrue(exited)
    }
}
