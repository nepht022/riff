package com.rodrigomachado.riff.core.ui.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class DifficultyDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var chosen: Int? = null
    private var dismissed = false

    private fun show() = composeRule.setContent {
        RiffTheme {
            DifficultyDialog(
                title = "Música",
                options = listOf(0, 3),
                label = { if (it == 0) "Fácil" else "Expert" },
                onChoose = { chosen = it },
                onDismiss = { dismissed = true },
            )
        }
    }

    @Test
    fun choosingAnOptionReportsIt() {
        show()

        composeRule.onNodeWithText("Música").assertExists()
        composeRule.onNodeWithText("Escolha a dificuldade").assertExists()
        composeRule.onNodeWithText("Expert").performClick()

        assertEquals(3, chosen)
    }

    @Test
    fun cancelDismisses() {
        show()

        composeRule.onNodeWithText("Cancelar").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun everyOptionHasTheSameTagAndTheFirstIsTheEasiest() {
        show()

        val options = composeRule.onAllNodesWithTag(DifficultyDialogTestTags.OPTION)
        options.assertCountEquals(2)
        options[0].performClick()

        assertEquals(0, chosen)
    }
}
