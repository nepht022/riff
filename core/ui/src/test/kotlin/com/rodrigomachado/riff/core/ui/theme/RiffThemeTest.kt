package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RiffThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun usesFixedPaletteEvenWhereDynamicColorExists() {
        // O Robolectric roda no SDK 35, que tem Dynamic Color (Android 12+).
        var background = Color.Unspecified
        var primary = Color.Unspecified
        var onBackground = Color.Unspecified
        composeRule.setContent {
            RiffTheme {
                background = MaterialTheme.colorScheme.background
                primary = MaterialTheme.colorScheme.primary
                onBackground = MaterialTheme.colorScheme.onBackground
            }
        }
        assertEquals(RiffColors.StageBlack, background)
        assertEquals(RiffColors.TourRed, primary)
        assertEquals(RiffColors.Paper, onBackground)
    }
}
