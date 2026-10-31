package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.test.junit4.createComposeRule
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

    private fun captured(): Pair<Typography, Shapes> {
        var typography: Typography? = null
        var shapes: Shapes? = null
        composeRule.setContent {
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
        assertEquals(NewRocker, typography.displayMedium.fontFamily)
        assertEquals(NewRocker, typography.headlineSmall.fontFamily)
        assertEquals(NewRocker, typography.titleLarge.fontFamily)
        assertEquals(NewRocker, typography.labelLarge.fontFamily)
    }

    @Test
    fun everythingElseUsesOswald() {
        val (typography, _) = captured()
        assertEquals(Oswald, typography.bodyMedium.fontFamily)
        assertEquals(Oswald, typography.titleMedium.fontFamily)
        assertEquals(FontWeight.SemiBold, typography.titleMedium.fontWeight)
        assertEquals(Oswald, typography.labelMedium.fontFamily)
    }

    @Test
    fun shapesAreAlmostSquare() {
        val (_, shapes) = captured()
        assertEquals(RoundedCornerShape(2.dp), shapes.small)
        assertEquals(RoundedCornerShape(4.dp), shapes.large)
    }
}
