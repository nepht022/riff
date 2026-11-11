package com.rodrigomachado.riff.core.ui.component

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class KeyHintBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val hints = listOf(
        KeyHint("← →", pad = null, label = "Mover"),
        KeyHint("Enter", pad = "✕", label = "Escolher"),
        KeyHint("Esc", pad = "○", label = "Voltar"),
    )

    @Test
    fun keyboardOnlyWithoutAPad() {
        composeRule.setContent { RiffTheme { KeyHintBar(hints, padConnected = false) } }
        composeRule.onNodeWithText("Enter").assertExists()
        composeRule.onNodeWithText("Escolher").assertExists()
        composeRule.onNodeWithText("✕").assertDoesNotExist()
        composeRule.onNodeWithText("○").assertDoesNotExist()
    }

    @Test
    fun padSymbolsWithAPad() {
        composeRule.setContent { RiffTheme { KeyHintBar(hints, padConnected = true) } }
        composeRule.onNodeWithText("Enter").assertExists()
        composeRule.onNodeWithText("✕").assertExists()
        composeRule.onNodeWithText("○").assertExists()
        composeRule.onNodeWithText("Mover").assertExists()
    }

    @Test
    fun backRowCallsOnBackAndIsTallEnoughToTouch() {
        var backs = 0
        composeRule.setContent { RiffTheme { BackRow(onBack = { backs++ }) } }
        composeRule.onNodeWithText("Voltar").assertHeightIsAtLeast(44.dp).performClick()
        assertEquals(1, backs)
    }
}
