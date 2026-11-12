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

/** As peças do design novo no PC: a barra de teclas (com e sem controle) e o Voltar com o foco do teclado. */
class NewPiecesScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val hints = listOf(
        KeyHint("← →", pad = null, label = "Mover"),
        KeyHint("Enter", pad = "✕", label = "Escolher"),
        KeyHint("Esc", pad = "○", label = "Voltar"),
    )

    private fun capture(
        name: String,
        width: Int = 720,
        focus: FocusRequester? = null,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            RiffTheme {
                Column(
                    Modifier.background(RiffColors.StageBlack).width(width.dp).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { content() }
            }
        }
        if (focus != null) composeRule.runOnIdle { focus.requestFocus() }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/desktop/$name.png", roborazziOptions = Tolerance)
    }

    @Test
    fun keyHintBarKeyboardOnly() = capture("KeyHintBar") { KeyHintBar(hints, padConnected = false) }

    @Test
    fun keyHintBarWithPad() = capture("KeyHintBar_pad") { KeyHintBar(hints, padConnected = true) }

    @Test
    fun backRowFocused() {
        val focus = FocusRequester()
        capture("BackRow_focused", width = 320, focus = focus) {
            BackRow(onBack = {}, modifier = Modifier.focusRequester(focus))
            BackRow(text = "Sair da sala", onBack = {})
        }
    }

    private companion object {
        val Tolerance = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))
    }
}
