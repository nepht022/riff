package com.rodrigomachado.riff.feature.nowplaying

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.NowPlaying
import com.rodrigomachado.riff.core.model.SpotifyError
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
class NowPlayingContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var played: PlayRequest? = null
    private var confirmed: Triple<String, String, Float>? = null
    private var chooseFor: Pair<String, String>? = null
    private var retries = 0
    private var libraryOpened = false
    private var settingsOpened = false

    private fun show(state: NowPlayingUiState) = composeRule.setContent {
        RiffTheme {
            NowPlayingContent(
                state = state,
                onRetry = { retries++ },
                onPlay = { played = it },
                onConfirm = { uri, id, confidence -> confirmed = Triple(uri, id, confidence) },
                onChooseChart = { uri, title -> chooseFor = uri to title },
                onOpenLibrary = { libraryOpened = true },
                onOpenSettings = { settingsOpened = true },
            )
        }
    }

    @Test
    fun matchedTrackPlaysWithTheChosenDifficultyAndItsOffset() {
        show(NowPlayingUiState.Playing(playing(MatchStatus.Matched(chart(), trackOffsetMs = 25))))

        composeRule.onNodeWithText("Bohemian Rhapsody - Remastered 2011").assertExists()
        composeRule.onNodeWithText("MAPA ENCONTRADO").assertExists()
        composeRule.onNodeWithText("Jogar").performClick()
        composeRule.onNodeWithText("Expert").performClick()

        assertEquals(PlayRequest("a", Difficulty.EXPERT, "spotify:track:x", 25), played)
    }

    @Test
    fun suggestionCanBeConfirmedOrReplaced() {
        show(NowPlayingUiState.Playing(playing(MatchStatus.Suggested(chart(), confidence = 0.9f))))

        composeRule.onNodeWithText("CONFIRMAR MAPA").assertExists()
        composeRule.onNodeWithText("Confirmar mapa").performClick()
        assertEquals(Triple("spotify:track:x", "a", 0.9f), confirmed)

        composeRule.onNodeWithText("Escolher outro").performClick()
        assertEquals("spotify:track:x" to "Bohemian Rhapsody - Remastered 2011", chooseFor)
    }

    @Test
    fun withoutAMatchAsksToChooseOne() {
        show(NowPlayingUiState.Playing(playing(MatchStatus.NoMatch, isPaused = true)))

        composeRule.onNodeWithText("SEM MAPA").assertExists()
        composeRule.onNodeWithText("Pausado no Spotify").assertExists()
        composeRule.onNodeWithText("Escolher mapa").performClick()

        assertEquals("spotify:track:x" to "Bohemian Rhapsody - Remastered 2011", chooseFor)
    }

    @Test
    fun spotifyNotInstalledStillOffersTheLibrary() {
        show(NowPlayingUiState.Unavailable(SpotifyError.NOT_INSTALLED))

        composeRule.onNodeWithText("Spotify não instalado").assertExists()
        composeRule.onNodeWithText("Abrir biblioteca").performClick()

        assertTrue(libraryOpened)
    }

    @Test
    fun otherFailuresOfferToTryAgain() {
        show(NowPlayingUiState.Unavailable(SpotifyError.NOT_LOGGED_IN))

        composeRule.onNodeWithText("Entre na sua conta no app do Spotify e tente de novo.").assertExists()
        composeRule.onNodeWithText("Tentar de novo").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun aMatchedChartCanBeReplaced() {
        show(NowPlayingUiState.Playing(playing(MatchStatus.Matched(chart(), 0))))

        composeRule.onNodeWithText("Escolher outro").performClick()

        assertEquals("spotify:track:x" to "Bohemian Rhapsody - Remastered 2011", chooseFor)
    }

    @Test
    fun trackChangingWhileChoosingTheDifficultyDoesNotMixTheTracks() {
        var state by mutableStateOf<NowPlayingUiState>(
            NowPlayingUiState.Playing(playing(MatchStatus.Matched(chart(), 0))),
        )
        composeRule.setContent {
            RiffTheme {
                NowPlayingContent(state, {}, { played = it }, { _, _, _ -> }, { _, _ -> }, {})
            }
        }
        composeRule.onNodeWithText("Jogar").performClick()

        val next = track().copy(trackUri = "spotify:track:y", title = "Outra")
        state = NowPlayingUiState.Playing(NowPlaying(next, MatchStatus.Matched(chart("b", title = "Outra"), 0)))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Escolha a dificuldade").assertDoesNotExist()
        assertEquals(null, played)
    }

    @Test
    fun settingsCanBeOpenedFromTheStartScreen() {
        show(NowPlayingUiState.Connecting)

        composeRule.onNodeWithText("Configurações").performClick()

        assertTrue(settingsOpened)
    }
}
