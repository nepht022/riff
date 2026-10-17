package com.rodrigomachado.riff.feature.nowplaying

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpRect
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.NowPlaying
import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyError
import com.rodrigomachado.riff.core.ui.component.LocalMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.core.ui.theme.Spacing
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
    private var reportOpened = false
    private val controls = mutableListOf<String>()
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
                    onOpenReport = { reportOpened = true },
                    onSearch = { searchedFor = it },
                    onPrevious = { controls += "previous" },
                    onPlayPause = { controls += "playPause:$it" },
                    onNext = { controls += "next" },
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
        composeRule.onNodeWithText("Escolher mapa").performScrollTo().performClick()

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
    fun aProblemCanBeReportedFromNextToTheSettings() {
        show(NowPlayingUiState.Connecting)

        composeRule.onNodeWithText("Reportar").performClick()

        assertTrue(reportOpened)
        assertEquals(listOf(MenuSound.NAVIGATE), sounds)
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

    /** O botão principal começa na mesma reta do texto do mapa, à esquerda. */
    @Test
    @Config(qualifiers = "pt-rBR-w360dp-h640dp")
    fun playLinesUpWithTheChartText() {
        show(NowPlayingUiState.Playing(playing(MatchStatus.Matched(chart(), 0))))
        assertLinedUp("Mapa: Bohemian Rhapsody — Queen", "Jogar")
    }

    @Test
    @Config(qualifiers = "pt-rBR-w360dp-h640dp")
    fun chooseLinesUpWithTheNoMatchText() {
        show(NowPlayingUiState.Playing(playing(MatchStatus.NoMatch)))
        assertLinedUp("Nenhum mapa da biblioteca casa com esta música.", "Escolher mapa")
    }

    private fun assertLinedUp(text: String, button: String) {
        val above = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()
        val below = composeRule.onAllNodesWithText(button).onLast().getUnclippedBoundsInRoot()
        assertEquals(above.left.value, below.left.value, 1f)
    }

    @Test
    fun theSongCanBeChangedOrPausedBeforeChoosingAMap() = useControls(MatchStatus.NoMatch)

    @Test
    fun theSongCanBeChangedOrPausedAfterChoosingAMap() = useControls(MatchStatus.Matched(chart(), 0))

    private fun useControls(match: MatchStatus) {
        show(NowPlayingUiState.Playing(playing(match)))

        composeRule.onNodeWithContentDescription("Música anterior").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Pausar música").performClick()
        composeRule.onNodeWithContentDescription("Próxima música").performClick()

        assertEquals(listOf("previous", "playPause:false", "next"), controls)
    }

    @Test
    fun pausedTheMiddleButtonPlays() {
        show(NowPlayingUiState.Playing(playing(MatchStatus.NoMatch, isPaused = true)))

        composeRule.onNodeWithContentDescription("Tocar música").performClick()

        assertEquals(listOf("playPause:true"), controls)
    }

    @Test
    fun withoutASongThereAreNoControls() {
        show(NowPlayingUiState.NothingPlaying)

        composeRule.onNodeWithContentDescription("Próxima música").assertDoesNotExist()
    }

    /** Onde a música está e quanto dura, como num player. */
    @Test
    fun theSongTimeIsShown() {
        show(NowPlayingUiState.Playing(playing(MatchStatus.NoMatch, isPaused = true)))

        composeRule.onNodeWithText("1:01").assertExists()
        composeRule.onNodeWithText("5:55").assertExists()
    }

    /** Jogar ocupa o mesmo espaço que Escolher mapa: trocar de música não faz os botões pularem de lugar. */
    @Test
    @Config(qualifiers = "pt-rBR-w360dp-h640dp")
    fun theMainButtonHasTheSameWidthWithOrWithoutAMap() {
        show(NowPlayingUiState.Playing(playing(MatchStatus.Matched(chart(), 0))))
        val play = composeRule.onNodeWithText("Jogar").performScrollTo().getUnclippedBoundsInRoot()
        assertEquals(MAIN_BUTTON_WIDTH.value, (play.right - play.left).value, 1f)
        assertCenteredBeside(play, "Escolher outro")
    }

    @Test
    @Config(qualifiers = "pt-rBR-w360dp-h640dp")
    fun theChooseButtonHasTheSameWidthAsPlay() {
        show(NowPlayingUiState.Playing(playing(MatchStatus.NoMatch)))
        val choose = composeRule.onNodeWithText("Escolher mapa").performScrollTo().getUnclippedBoundsInRoot()
        assertEquals(MAIN_BUTTON_WIDTH.value, (choose.right - choose.left).value, 1f)
        assertCenteredBeside(choose, "Pesquisar no Encore")
    }

    /** O link ocupa todo o espaço livre à direita do botão principal (e o texto fica no meio dele). */
    private fun assertCenteredBeside(button: DpRect, link: String) {
        val other = composeRule.onNode(hasText(link) and hasClickAction()).getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertEquals((root.right - Spacing.md).value, other.right.value, 1f)
        assertTrue("$button, $other", other.left >= button.right + Spacing.sm && other.left - button.right < Spacing.md)
    }

    @Test
    fun aRequestedDifficultyChoiceOpensTheList() {
        var asked = 0
        composeRule.setContent {
            RiffTheme {
                NowPlayingContent(
                    state = NowPlayingUiState.Playing(playing(MatchStatus.Matched(chart(), 0))),
                    onRetry = {},
                    onPlay = { played = it },
                    onConfirm = { _, _, _ -> },
                    onChooseChart = { _, _ -> },
                    onOpenLibrary = {},
                    askDifficultyFor = "spotify:track:x",
                    onDifficultyAsked = { asked++ },
                )
            }
        }

        composeRule.onNodeWithText("Escolha a dificuldade").assertExists()
        assertEquals(1, asked)
    }
}
