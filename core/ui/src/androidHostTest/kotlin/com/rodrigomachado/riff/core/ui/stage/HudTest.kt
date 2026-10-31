package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class HudTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "pt-rBR")
    fun scoreUsesBrazilianThousandsSeparator() {
        composeRule.setContent { RiffTheme { ScoreText(48_300) } }
        composeRule.onNodeWithText("48.300").assertExists()
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun scoreUsesEnglishThousandsSeparator() {
        composeRule.setContent { RiffTheme { ScoreText(48_300) } }
        composeRule.onNodeWithText("48,300").assertExists()
    }

    @Test
    fun multiplierShowsAsXN() {
        composeRule.setContent { RiffTheme { MultiplierStamp(3) } }
        composeRule.onNodeWithText("x3").assertExists()
    }
}
