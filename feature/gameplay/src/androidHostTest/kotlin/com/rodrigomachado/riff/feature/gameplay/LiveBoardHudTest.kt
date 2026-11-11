package com.rodrigomachado.riff.feature.gameplay

import androidx.activity.ComponentActivity
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
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

/** O placar ao vivo minimizado na partida de sala: as linhas dos outros, a minha destacada, a troca animada. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class LiveBoardHudTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val others = mutableStateOf(
        listOf(
            LiveScore("ana", "Ana", 2_000, 10, 10, 0.4f, finished = false),
            LiveScore("bia", "Bia", 500, 2, 3, 0.1f, finished = false),
        ),
    )

    private fun show(liveOthers: State<List<LiveScore>>? = others, reducedMotion: Boolean = true) {
        val session =
            GameSession(listOf(note(60_000)), FakeSongPlayer(), FakeSfxPlayer(), FakeTime(), audioOffsetMs = 0)
        val state = GameplayUiState.Ready(
            "Música",
            "Banda",
            Difficulty.EXPERT,
            session,
            Settings.DEFAULT,
            inRoom = liveOthers != null,
        )
        composeRule.setContent {
            RiffTheme {
                GameStage(
                    state,
                    reducedMotion = reducedMotion,
                    onPause = {},
                    onResume = {},
                    onExit = {},
                    onSessionFinished = {},
                    animate = false,
                    liveOthers = liveOthers,
                )
            }
        }
    }

    private fun line(uid: String) = composeRule.onNodeWithTag(GameplayTestTags.liveLine(uid))

    private fun top(uid: String): Dp = line(uid).getUnclippedBoundsInRoot().top

    @Test
    fun othersAppearOnTheHud() {
        show()

        composeRule.onNodeWithTag(GameplayTestTags.LIVE_BOARD).assertIsDisplayed()
        line("ana").assertIsDisplayed()
        line("ana").assert(hasText("Ana", substring = true))
        line("bia").assert(hasText("Bia", substring = true))
        // Na ordem: Ana (2.000), Bia (500), eu (0).
        assertTrue(top("ana") < top("bia"))
        assertTrue(top("bia") < top(GameplayTestTags.LIVE_ME))
    }

    @Test
    fun myLineIsHighlighted() {
        show()

        line(GameplayTestTags.LIVE_ME).assertIsSelected()
        line(GameplayTestTags.LIVE_ME).assert(hasText("Você", substring = true))
        line("ana").assertIsNotSelected()
    }

    /** Bia passa a Ana: as duas linhas deslizam (no caminho, a Bia passa por lugares entre os dois). */
    @Test
    fun overtakingAnimatesTheSwap() {
        show(reducedMotion = false)
        composeRule.waitForIdle()
        val anaBefore = top("ana")
        val biaBefore = top("bia")

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { others.value = listOf(others.value[0], others.value[1].copy(score = 3_000)) }
        // Quadro a quadro, até o dobro da animação: onde a Bia esteve.
        val path = (0 until (LIVE_SWAP_MS * 2 / FRAME_MS).toInt()).map {
            composeRule.mainClock.advanceTimeBy(FRAME_MS)
            top("bia")
        }

        assertTrue("caminho da Bia: $path", path.any { it < biaBefore && it > anaBefore })
        assertEquals(anaBefore, path.last())
        assertEquals(biaBefore, top("ana"))
        line("bia").assert(hasText("1", substring = true))
    }

    @Test
    fun staleLineSaysNoConnection() {
        others.value = listOf(others.value[0].copy(stale = true), others.value[1].copy(left = true))
        show()

        line("ana").assert(hasText("sem conexão", substring = true))
        line("bia").assert(hasText("saiu", substring = true))
        // Quem saiu fica por último, mesmo com mais pontos do que eu.
        assertTrue(top(GameplayTestTags.LIVE_ME) < top("bia"))
    }

    @Test
    fun soloMatchHasNoLiveBoard() {
        show(liveOthers = null)

        composeRule.onNodeWithTag(GameplayTestTags.LIVE_BOARD).assertDoesNotExist()
    }

    private companion object {
        const val FRAME_MS = 16L
    }
}
