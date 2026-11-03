package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test

/**
 * O destaque do foco no PC, no palco escuro: o botão de pôster (vermelho) e o cartão de música (papel), com e sem o
 * foco. Com até 1% dos pixels de folga, como nos outros screenshots do PC.
 */
class FocusHighlightScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, focused: Boolean, content: @Composable (Modifier) -> Unit) {
        val focus = FocusRequester()
        composeRule.setContent {
            RiffTheme {
                Column(
                    Modifier.background(RiffColors.StageBlack).width(320.dp).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { content(Modifier.focusRequester(focus)) }
            }
        }
        if (focused) composeRule.runOnIdle { focus.requestFocus() }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/desktop/$name.png", roborazziOptions = Tolerance)
    }

    @Composable
    private fun Button(modifier: Modifier) = PosterButton("Jogar", onClick = {}, modifier = modifier)

    @Composable
    private fun Card(modifier: Modifier) = TrackCard("Riff de teste", "Banda da garagem", modifier, onClick = {})

    @Test
    fun posterButtonWithoutFocus() = capture("FocusHighlight_button", focused = false) { Button(it) }

    @Test
    fun posterButtonWithFocus() = capture("FocusHighlight_button_focused", focused = true) { Button(it) }

    @Test
    fun trackCardWithoutFocus() = capture("FocusHighlight_card", focused = false) { Card(it) }

    @Test
    fun trackCardWithFocus() = capture("FocusHighlight_card_focused", focused = true) { Card(it) }

    private companion object {
        val Tolerance = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))
    }
}
