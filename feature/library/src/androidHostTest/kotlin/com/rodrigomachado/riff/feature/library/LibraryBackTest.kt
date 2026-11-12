package com.rodrigomachado.riff.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** O voltar do Android na Biblioteca: no modo Selecionar, cancela a seleção em vez de sair da tela. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class LibraryBackTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<String>()

    private fun show(selecting: Boolean) = composeRule.setContent {
        RiffTheme {
            LibraryContent(
                state = LibraryUiState(
                    isLoading = false,
                    hasFolder = true,
                    charts = listOf(chart("a", title = "Música A")),
                    deletion = DeletionState(selecting = selecting),
                ),
                onPickFolder = {},
                onRescan = {},
                onReportDismissed = {},
                onPlay = { _, _ -> },
                onBack = { events += "back" },
                deletion = DeletionCallbacks(onCancelSelecting = { events += "cancelSelect" }),
            )
        }
    }

    @Test
    fun backWhileSelectingCancelsTheSelection() {
        show(selecting = true)

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(listOf("cancelSelect"), events)
    }

    @Test
    fun backOutsideSelectingIsNotTakenByTheLibrary() {
        show(selecting = false)

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(emptyList<String>(), events)
    }
}
