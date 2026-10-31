package com.rodrigomachado.riff.core.ui.component

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PosterComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun posterButtonCallsOnClickAndIsAtLeast48dpTall() {
        var clicks = 0
        composeRule.setContent { RiffTheme { PosterButton("Jogar", onClick = { clicks++ }) } }

        composeRule.onNodeWithText("Jogar").performClick()

        assertEquals(1, clicks)
        composeRule.onNodeWithText("Jogar").assertHeightIsAtLeast(24.dp) // o texto em si
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role)).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun disabledPosterButtonDoesNotClick() {
        var clicks = 0
        composeRule.setContent { RiffTheme { PosterButton("Jogar", onClick = { clicks++ }, enabled = false) } }

        composeRule.onNodeWithText("Jogar").performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun posterTitleIsReadOnceAsHeading() {
        composeRule.setContent { RiffTheme { PosterTitle("RIFF") } }

        composeRule.onAllNodesWithText("RIFF").assertCountEquals(1)
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
    }

    @Test
    fun clickableTrackCardCallsOnClick() {
        var clicks = 0
        composeRule.setContent { RiffTheme { TrackCard("Música", "Banda", onClick = { clicks++ }) } }

        composeRule.onNodeWithText("Música").performClick()

        assertEquals(1, clicks)
    }
}
