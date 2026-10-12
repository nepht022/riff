package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.stage.HighwayGeometry
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameStageTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val time = FakeTime()
    private val player = FakeSongPlayer()
    private val sfx = FakeSfxPlayer()

    private fun show(leftHanded: Boolean = false): GameSession {
        val session = GameSession(listOf(note(60_000)), player, sfx, time, audioOffsetMs = 0).also { it.start() }
        val state = GameplayUiState.Ready(
            "Música",
            "Banda",
            Difficulty.EXPERT,
            session,
            Settings.DEFAULT.copy(leftHanded = leftHanded),
        )
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            RiffTheme {
                GameStage(
                    state,
                    reducedMotion = true,
                    onPause = {},
                    onResume = {},
                    onExit = {},
                    onSessionFinished = {},
                )
            }
        }
        return session
    }

    private fun laneX(lane: Int, width: Float, height: Float) = HighwayGeometry(
        Size(width, height),
    ).laneCenter(lane, 0f).x

    @Test
    fun twoFingersPressAndReleaseTwoLanesIndependently() {
        val session = show()

        composeRule.onNodeWithTag(GameplayTestTags.STAGE).performTouchInput {
            val y = height * 0.8f
            down(0, Offset(laneX(1, width.toFloat(), height.toFloat()), y))
            down(1, Offset(laneX(3, width.toFloat(), height.toFloat()), y))
        }
        assertTrue(session.isPressed(1))
        assertTrue(session.isPressed(3))

        composeRule.onNodeWithTag(GameplayTestTags.STAGE).performTouchInput { up(0) }
        assertFalse(session.isPressed(1))
        assertTrue(session.isPressed(3))
        assertEquals(2, sfx.misses) // nenhuma nota perto: dois toques fantasmas
    }

    @Test
    fun leftHandedMirrorsTheLanes() {
        val session = show(leftHanded = true)

        composeRule.onNodeWithTag(GameplayTestTags.STAGE).performTouchInput {
            down(0, Offset(laneX(0, width.toFloat(), height.toFloat()), height * 0.8f))
        }

        assertTrue(session.isPressed(4))
    }

    @Test
    fun thePauseButtonStaysInTheCenterWhateverTheScoreWidth() {
        show()

        // Antes ficava entre o placar e o multiplicador: o placar crescendo empurrava o botão para o lado.
        val stage = composeRule.onNodeWithTag(GameplayTestTags.STAGE).fetchSemanticsNode().boundsInRoot
        val pause = composeRule.onNodeWithTag(GameplayTestTags.PAUSE).fetchSemanticsNode().boundsInRoot
        assertEquals(stage.center.x, pause.center.x, 1f)
    }
}
