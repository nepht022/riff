package com.rodrigomachado.riff.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class HomeContentTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val events = mutableListOf<String>()

    private fun show() = composeRule.setContent {
        RiffTheme {
            HomeScreen(
                onSolo = { events += "solo" },
                onRanking = { events += "ranking" },
                onSettings = { events += "settings" },
                onReport = { events += "report" },
            )
        }
    }

    @Test
    fun soloOpensTheLibraryAndRankingOpensTheRanking() {
        show()

        composeRule.onNodeWithTag(HomeTestTags.SOLO).performClick()
        composeRule.onNodeWithTag(HomeTestTags.RANKING).performClick()

        assertEquals(listOf("solo", "ranking"), events)
    }

    @Test
    fun theCornerIconsOpenReportAndSettings() {
        show()

        composeRule.onNodeWithContentDescription("Reportar problema").performClick()
        composeRule.onNodeWithContentDescription("Configurações").performClick()

        assertEquals(listOf("report", "settings"), events)
    }

    @Test
    fun theRoomIsComingSoonAndDoesNothing() {
        show()

        val room = composeRule.onNodeWithTag(HomeTestTags.ROOM)
        room.assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Sala, em breve").assertExists()
        room.performClick()

        assertEquals(emptyList<String>(), events)
    }

    /** Num celular baixo os quadros encolhem: o Ranking continua na tela. */
    @Test
    @Config(qualifiers = "pt-rBR-w320dp-h480dp")
    fun onAShortScreenTheRankingStillShows() {
        show()

        composeRule.onNodeWithTag(HomeTestTags.RANKING).assertIsDisplayed()
    }

    /** Deitado e com a fonte grande do Android, nada cabe: a tela rola até o Ranking. */
    @Test
    @Config(qualifiers = "pt-rBR-w780dp-h360dp-land", fontScale = 2f)
    fun inLandscapeWithALargeFontTheRankingIsReachedByScrolling() {
        show()

        composeRule.onNodeWithTag(HomeTestTags.RANKING).performScrollTo().assertIsDisplayed().performClick()

        assertEquals(listOf("ranking"), events)
    }

    /** Com o Riff em inglês (o Locale padrão do processo, como o app define), a tela inicial fica em inglês. */
    @Test
    @Config(qualifiers = "en")
    fun inEnglishTheTilesAreInEnglish() {
        val system = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            show()
            composeRule.waitForIdle()
        } finally {
            Locale.setDefault(system)
        }

        composeRule.onNodeWithText("Solo").assertExists() // pt: "Sozinho"
    }
}
