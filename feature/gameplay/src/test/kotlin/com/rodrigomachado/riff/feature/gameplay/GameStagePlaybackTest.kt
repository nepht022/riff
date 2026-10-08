package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.test.junit4.createComposeRule
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameStagePlaybackTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun whenTheMusicStopsOutsideTheGameTheStageReportsIt() {
        val player = FakeSongPlayer()
        val session = GameSession(listOf(note(60_000)), player, FakeSfxPlayer(), FakeTime(), audioOffsetMs = 0)
        session.start()
        val reported = mutableListOf<Boolean>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            RiffTheme {
                GameStage(
                    GameplayUiState.Ready("Música", "Banda", Difficulty.EXPERT, session, Settings.DEFAULT),
                    reducedMotion = true,
                    onPause = {},
                    onResume = {},
                    onExit = {},
                    onSessionFinished = {},
                    onPlaybackChanged = { reported += it },
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(emptyList<Boolean>(), reported)

        player.pause() // pausado pela notificação do Spotify
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()

        assertEquals(true, reported.firstOrNull())
    }
}
