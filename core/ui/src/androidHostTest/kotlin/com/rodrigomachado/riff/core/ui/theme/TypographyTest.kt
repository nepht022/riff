package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TypographyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var newRocker: FontFamily
    private lateinit var oswald: FontFamily

    private fun captured(): Pair<Typography, Shapes> {
        var typography: Typography? = null
        var shapes: Shapes? = null
        composeRule.setContent {
            newRocker = newRocker()
            oswald = oswald()
            RiffTheme {
                typography = MaterialTheme.typography
                shapes = MaterialTheme.shapes
            }
        }
        return typography!! to shapes!!
    }

    @Test
    fun titlesAndMainButtonsUseNewRocker() {
        val (typography, _) = captured()
        assertEquals(newRocker, typography.displayMedium.fontFamily)
        assertEquals(newRocker, typography.headlineSmall.fontFamily)
        assertEquals(newRocker, typography.titleLarge.fontFamily)
        assertEquals(newRocker, typography.labelLarge.fontFamily)
    }

    @Test
    fun everythingElseUsesOswald() {
        val (typography, _) = captured()
        assertEquals(oswald, typography.bodyMedium.fontFamily)
        assertEquals(oswald, typography.titleMedium.fontFamily)
        assertEquals(FontWeight.SemiBold, typography.titleMedium.fontWeight)
        assertEquals(oswald, typography.labelMedium.fontFamily)
    }

    @Test
    fun shapesAreAlmostSquare() {
        val (_, shapes) = captured()
        assertEquals(RoundedCornerShape(2.dp), shapes.small)
        assertEquals(RoundedCornerShape(4.dp), shapes.large)
    }
}
