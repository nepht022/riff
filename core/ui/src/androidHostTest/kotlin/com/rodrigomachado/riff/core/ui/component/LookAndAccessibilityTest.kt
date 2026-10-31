package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.stage.JudgmentText
import com.rodrigomachado.riff.core.ui.stage.ScoreText
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class LookAndAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
    }

    @Test
    fun theScoreIsNotReadAsAHeading() {
        composeRule.setContent { RiffTheme { ScoreText(48_300) } }

        composeRule.onNodeWithText("48.300").assertExists()
        composeRule.onNode(isHeading()).assertDoesNotExist()
    }

    @Test
    fun aFocusedPosterButtonShowsAFocusRing() {
        composeRule.setContent { RiffTheme { PosterButton("Jogar", onClick = {}) } }
        composeRule.onNodeWithTag(PosterButtonTestTags.FOCUS_RING).assertDoesNotExist()

        composeRule.onNodeWithText("Jogar").requestFocus() // teclado ou controle

        composeRule.onNodeWithTag(PosterButtonTestTags.FOCUS_RING).assertExists()
    }

    @Test
    fun aLabelThatWrapsStaysCentered() {
        composeRule.setContent {
            RiffTheme {
                PosterButton(
                    "Calibrar atraso do áudio agora",
                    onClick = {},
                    modifier = Modifier.width(160.dp),
                )
            }
        }

        assertEquals(
            TextAlign.Center,
            composeRule.onNodeWithText("Calibrar atraso do áudio agora").layout().layoutInput.style.textAlign,
        )
    }

    @Test
    @Config(qualifiers = "pt-rBR-xxhdpi")
    fun theJudgmentGlowIsTheSameSizeOnEveryScreenDensity() {
        composeRule.setContent { RiffTheme { JudgmentText("PERFEITO!") } }

        val blur = composeRule.onNodeWithText("PERFEITO!").layout().layoutInput.style.shadow?.blurRadius
        assertEquals(36f, blur) // 12 dp em xxhdpi (densidade 3)
    }

    @Test
    fun theErrorMessageUsesDimmedPaper() {
        composeRule.setContent { RiffTheme { ErrorState("Perdi o acesso à pasta.", onRetry = {}) } }

        assertEquals(
            RiffColors.PaperDim,
            composeRule.onNodeWithText("Perdi o acesso à pasta.").layout().layoutInput.style.color,
        )
    }

    @Test
    fun thePosterButtonStaysTappableWithTheLargestFont() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                RiffTheme { PosterButton("Jogar", onClick = {}) }
            }
        }

        composeRule.onNodeWithText("Jogar").assertHeightIsAtLeast(48.dp)
    }
}
