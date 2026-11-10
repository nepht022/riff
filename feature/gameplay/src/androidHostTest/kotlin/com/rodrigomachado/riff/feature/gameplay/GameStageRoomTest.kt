package com.rodrigomachado.riff.feature.gameplay

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A partida de sala na tela: sem o botão de pausa, a contagem 3-2-1 por cima da pista e o Voltar que pergunta. */
@RunWith(RobolectricTestRunner::class)
class GameStageRoomTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val time = FakeTime(now = 10_000)
    private var pauses = 0
    private var exits = 0

    private fun show(countdownEndsAt: Long? = null, waiting: Boolean = false) {
        val session = GameSession(listOf(note(60_000)), FakeSongPlayer(), FakeSfxPlayer(), time, audioOffsetMs = 0)
        val state = GameplayUiState.Ready(
            "Música",
            "Banda",
            Difficulty.EXPERT,
            session,
            Settings.DEFAULT,
            inRoom = true,
            waitingForStart = waiting,
            countdownEndsAt = countdownEndsAt,
        )
        composeRule.setContent {
            RiffTheme {
                GameStage(
                    state,
                    reducedMotion = true,
                    onPause = { pauses++ },
                    onResume = {},
                    onExit = { exits++ },
                    onSessionFinished = {},
                    animate = false,
                )
            }
        }
    }

    private fun pressBack() = composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun theRoomMatchHasNoPauseButton() {
        show()

        composeRule.onNodeWithTag(GameplayTestTags.PAUSE).assertDoesNotExist()
    }

    @Test
    fun backAsksBeforeLeavingWithoutPausing() {
        show()

        pressBack()

        assertEquals(0, pauses)
        composeRule.onNodeWithTag(GameplayTestTags.LEAVE_PROMPT).assertIsDisplayed()
        composeRule.onNodeWithTag(GameplayTestTags.KEEP_PLAYING).performClick()
        composeRule.onNodeWithTag(GameplayTestTags.LEAVE_PROMPT).assertDoesNotExist()

        pressBack()
        composeRule.onNodeWithTag(GameplayTestTags.EXIT).performClick()
        assertEquals(1, exits)
        assertEquals(0, pauses)
    }

    @Test
    fun theCountdownShowsTheSecondsLeft() {
        show(countdownEndsAt = time.now + 2_500)

        composeRule.onNodeWithTag(GameplayTestTags.COUNTDOWN).assertIsDisplayed()
        composeRule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun waitingForTheOthersIsShownOverTheTrack() {
        show(waiting = true)

        composeRule.onNodeWithTag(GameplayTestTags.WAITING).assertIsDisplayed()
    }
}
