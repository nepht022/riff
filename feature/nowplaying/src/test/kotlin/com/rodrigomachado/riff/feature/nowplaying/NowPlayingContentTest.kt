package com.rodrigomachado.riff.feature.nowplaying

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.NowPlaying
import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyError
import com.rodrigomachado.riff.core.ui.component.LocalMenuSound
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
    private val sounds = mutableListOf<MenuSound>()

    @get:Rule
    val composeRule = createComposeRule()

    private var played: PlayRequest? = null
    private var confirmed: Triple<String, String, Float>? = null
    private var chooseFor: Pair<String, String>? = null
    private var retries = 0
    private var libraryOpened = false
    private var settingsOpened = false
    private var searchedFor: PlaybackState? = null

    private fun show(state: NowPlayingUiState) = composeRule.setContent {
        CompositionLocalProvider(LocalMenuSound provides { sounds += it }) {
            RiffTheme {
                NowPlayingContent(
                    state = state,
                    onRetry = { retries++ },
                    onPlay = { played = it },
                    onConfirm = { uri, id, confidence -> confirmed = Triple(uri, id, confidence) },
                    onChooseChart = { uri, title -> chooseFor = uri to title },
                    onOpenLibrary = { libraryOpened = true },
                    onOpenSettings = { settingsOpened = true },
                    onSearch = { searchedFor = it },
                )
            }
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

    @Test
    fun theLibraryButtonHasATagForTheAutomatedJourneys() {
        show(NowPlayingUiState.Unavailable(SpotifyError.NOT_INSTALLED))

        composeRule.onNodeWithTag(NowPlayingTestTags.OPEN_LIBRARY).performClick()

        assertTrue(libraryOpened)
    }

    @Test
    fun theDifficultyDialogSurvivesRotatingTheScreen() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            RiffTheme {
                NowPlayingContent(
                    state = NowPlayingUiState.Playing(playing(MatchStatus.Matched(chart(), trackOffsetMs = 25))),
                    onRetry = {},
                    onPlay = { played = it },
                    onConfirm = { _, _, _ -> },
                    onChooseChart = { _, _ -> },
                    onOpenLibrary = {},
                    onOpenSettings = {},
                )
            }
        }
        composeRule.onNodeWithText("Jogar").performClick()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Expert").performClick()
        assertEquals(PlayRequest("a", Difficulty.EXPERT, "spotify:track:x", 25), played)
    }

    @Test
    fun menuButtonsPlayTheSoundOfTheirRole() {
        show(NowPlayingUiState.Playing(playing(MatchStatus.Matched(chart(), trackOffsetMs = 0))))

        composeRule.onNodeWithText("Biblioteca").performClick()
        composeRule.onNodeWithText("Escolher outro").performClick()
        composeRule.onNodeWithText("Jogar").performClick()
        composeRule.onNodeWithText("Expert").performClick()

        assertEquals(listOf(MenuSound.NAVIGATE, MenuSound.NAVIGATE, MenuSound.CONFIRM, MenuSound.CONFIRM), sounds)
    }

    @Test
    fun aTrackWithoutAChartOffersToSearchEncoreForIt() {
        show(NowPlayingUiState.Playing(playing(MatchStatus.NoMatch)))

        composeRule.onNodeWithText("Pesquisar no Encore").performClick()

        assertEquals("Bohemian Rhapsody - Remastered 2011", searchedFor?.title)
        assertEquals(listOf(MenuSound.NAVIGATE), sounds)
    }

    @Test
    @Config(qualifiers = "pt-rBR-w360dp-h640dp")
    fun searchEncoreSitsBesideChooseChart() {
        show(NowPlayingUiState.Playing(playing(MatchStatus.NoMatch)))
        assertBeside("Escolher mapa", "Pesquisar no Encore")
    }

    @Test
    @Config(qualifiers = "pt-rBR-w360dp-h640dp")
    fun chooseOtherSitsBesideConfirm() {
        show(NowPlayingUiState.Playing(playing(MatchStatus.Suggested(chart(), 0.9f))))
        assertBeside("Confirmar mapa", "Escolher outro")
    }

    @Test
    @Config(qualifiers = "pt-rBR-w360dp-h640dp")
    fun chooseOtherSitsBesidePlay() {
        show(NowPlayingUiState.Playing(playing(MatchStatus.Matched(chart(), 0))))
        assertBeside("Jogar", "Escolher outro")
    }

    /** Na mesma linha: o segundo começa à direita do primeiro e as alturas se cruzam. */
    private fun assertBeside(first: String, second: String) {
        // O último com o texto: "Confirmar mapa" também é a etiqueta do cartão, que vem antes.
        val a = composeRule.onAllNodesWithText(first).onLast().getUnclippedBoundsInRoot()
        val b = composeRule.onAllNodesWithText(second).onLast().getUnclippedBoundsInRoot()
        assertTrue("$first: $a, $second: $b", b.left >= a.right && b.top < a.bottom && a.top < b.bottom)
    }

    @Test
    @Config(qualifiers = "pt-rBR-w360dp-h640dp")
    fun playAndChooseOtherAreCentered() {
        show(NowPlayingUiState.Playing(playing(MatchStatus.Matched(chart(), 0))))

        val play = composeRule.onAllNodesWithText("Jogar").onLast().getUnclippedBoundsInRoot()
        val other = composeRule.onAllNodesWithText("Escolher outro").onLast().getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertEquals((root.left + root.right).value / 2, (play.left + other.right).value / 2, 1f)
    }
}
