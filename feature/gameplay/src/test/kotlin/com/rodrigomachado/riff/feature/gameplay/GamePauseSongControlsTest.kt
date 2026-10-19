package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.rodrigomachado.riff.core.audio.PlayerNotice
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Na pausa do modo Spotify: anterior, tocar (continua a partida) e próxima. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class GamePauseSongControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    private fun showPaused(spotify: Boolean, notice: PlayerNotice? = null) {
        val session = GameSession(
            listOf(note(60_000)),
            FakeSongPlayer().apply { this.notice = notice },
            FakeSfxPlayer(),
            FakeTime(),
            audioOffsetMs = 0,
        )
        val state = GameplayUiState.Ready(
            "Música",
            "Banda",
            Difficulty.EXPERT,
            session,
            Settings.DEFAULT,
            isPaused = true,
            spotifyTrackUri = if (spotify) "spotify:track:x" else null,
        )
        composeRule.setContent {
            RiffTheme {
                GameStage(
                    state,
                    reducedMotion = true,
                    onPause = {},
                    onResume = { events += "resume" },
                    onExit = { events += "exit" },
                    onSessionFinished = {},
                    onChangeSong = { forward -> events += if (forward) "next" else "previous" },
                    animate = false,
                )
            }
        }
    }

    @Test
    fun inSpotifyModeTheSongCanBeChangedFromThePause() {
        showPaused(spotify = true)

        composeRule.onNodeWithContentDescription("Tocar música").performClick()
        composeRule.onNodeWithContentDescription("Próxima música").performClick()
        composeRule.onNodeWithContentDescription("Música anterior").performClick()

        // Trocar de música encerra a partida: quem chama (a tela) sai e tenta começar a próxima.
        assertEquals(listOf("resume", "next", "previous"), events)
    }

    /** "Jogar de novo" sem conseguir voltar a música no Spotify (conta grátis, outra faixa): a pausa explica. */
    @Test
    fun aRefusedTrackShowsGoBackInThePause() {
        showPaused(spotify = true, notice = PlayerNotice.GO_BACK_TO_TRACK)

        composeRule.onNodeWithText("Volte a música no Spotify para jogar de novo").assertExists()
    }

    @Test
    fun aLostSpotifyIsExplainedInThePause() {
        showPaused(spotify = true, notice = PlayerNotice.SOURCE_LOST)

        composeRule.onNodeWithText("O Riff perdeu o Spotify", substring = true).assertExists()
    }

    @Test
    fun anAdIsExplainedInThePause() {
        showPaused(spotify = true, notice = PlayerNotice.AD)

        composeRule.onNodeWithText("Anúncio no Spotify: a partida continua quando ele acabar.").assertExists()
    }

    @Test
    fun withTheTrackPlayingThereIsNoGoBackNotice() {
        showPaused(spotify = true)

        composeRule.onNodeWithText("Volte a música no Spotify para jogar de novo").assertDoesNotExist()
    }

    @Test
    fun inTheLibraryThereAreNoSongControls() {
        showPaused(spotify = false)

        composeRule.onNodeWithContentDescription("Próxima música").assertDoesNotExist()
    }
}
