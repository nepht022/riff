package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class FocusHighlightTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun pixels(): List<Int> {
        val map = composeRule.onRoot().captureToImage().toPixelMap()
        return List(map.width * map.height) { map[it % map.width, it / map.width].hashCode() }
    }

    @Test
    fun onThePcTheFocusedElementLooksDifferent() {
        val focus = FocusRequester()
        composeRule.setContent {
            Box(Modifier.background(RiffColors.StageBlack).padding(16.dp)) {
                Box(
                    Modifier
                        .size(80.dp, 48.dp)
                        .focusRequester(focus)
                        .focusHighlight()
                        .focusable()
                        .background(RiffColors.TourRed),
                )
            }
        }
        val unfocused = pixels()

        composeRule.runOnIdle { focus.requestFocus() }

        assertNotEquals("o foco não mudou nada no desenho", unfocused, pixels())
    }
}
