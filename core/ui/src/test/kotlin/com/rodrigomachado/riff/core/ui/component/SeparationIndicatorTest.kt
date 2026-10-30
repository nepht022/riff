package com.rodrigomachado.riff.core.ui.component

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.model.QueuedSeparation
import com.rodrigomachado.riff.core.model.SeparationFailure
import com.rodrigomachado.riff.core.model.SeparationStatus
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class SeparationIndicatorTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val events = mutableListOf<String>()

    private fun show(status: SeparationStatus, queued: List<String> = emptyList(), waitingForCharger: Boolean = false) =
        composeRule.setContent {
            RiffTheme {
                SeparationIndicatorContent(
                    status,
                    onCancel = { events += "cancel" },
                    onDismissFailure = { events += "ok" },
                    queue = SeparationQueueView(
                        items = queued.map { QueuedSeparation(it, it) },
                        waitingForCharger = waitingForCharger,
                        onRemove = { events += "remove $it" },
                        onCancelAll = { events += "all" },
                    ),
                )
            }
        }

    private fun openPanel() = composeRule.onNodeWithTag(SeparationIndicatorTags.ICON).performClick()

    @Test
    fun idleShowsNothing() {
        show(SeparationStatus.Idle)
        composeRule.onNodeWithTag(SeparationIndicatorTags.ICON).assertDoesNotExist()
    }

    @Test
    fun theIconOpensThePanelWithProgressTimeAndCancel() {
        show(SeparationStatus.Running("m", "Smells Like Teen Spirit", 0.45f, remainingMs = 70_000))

        composeRule.onNodeWithTag(SeparationIndicatorTags.ICON).performClick()

        composeRule.onNodeWithText("Smells Like Teen Spirit").assertExists()
        composeRule.onNodeWithText("Separando a guitarra… 45%").assertExists()
        composeRule.onNodeWithText("cerca de 2 min").assertExists()
        composeRule.onNodeWithText("Cancelar").performClick()
        assertEquals(listOf("cancel"), events)
    }

    @Test
    fun pausedForHeatSaysSo() {
        show(SeparationStatus.Running("m", "M", 0.2f, null, pausedForHeat = true))

        composeRule.onNodeWithTag(SeparationIndicatorTags.ICON).performClick()

        composeRule.onNodeWithText("Pausado para esfriar…").assertExists()
    }

    @Test
    fun aFailureIsShownUntilDismissed() {
        show(SeparationStatus.Failed("M", SeparationFailure.ERROR))

        composeRule.onNodeWithTag(SeparationIndicatorTags.ICON).performClick()
        composeRule.onNodeWithText("Não deu para separar a guitarra de M").assertExists()
        composeRule.onNodeWithText("OK").performClick()

        assertEquals(listOf("ok"), events)
    }

    @Test
    fun theIconCountsTheSongsLeft() {
        show(SeparationStatus.Running("m", "M", 0.2f, null), queued = listOf("B", "C"))

        composeRule.onNodeWithText("3").assertExists()
    }

    @Test
    fun aSingleSongHasNoCount() {
        show(SeparationStatus.Running("m", "M", 0.2f, null))

        composeRule.onNodeWithText("1").assertDoesNotExist()
    }

    @Test
    fun thePanelListsTheQueueWithAnXForEachAndCancelsOnlyTheCurrentOrAll() {
        show(SeparationStatus.Running("m", "Atual", 0.45f, null), queued = listOf("Bê", "Cê"))
        openPanel()

        composeRule.onNodeWithText("Na fila (2)").assertExists()
        composeRule.onNodeWithContentDescription("Tirar Cê da fila").performClick()
        composeRule.onNodeWithText("Cancelar esta").performClick()
        openPanel()
        composeRule.onNodeWithText("Cancelar todas").performClick()

        assertEquals(listOf("remove Cê", "cancel", "all"), events)
    }

    @Test
    fun aQueueWaitingForThePlugSaysSo() {
        show(SeparationStatus.Idle, queued = listOf("Bê"), waitingForCharger = true)
        openPanel()

        composeRule.onNodeWithText("Esperando o carregador").assertExists()
        composeRule.onNodeWithText("Bê").assertExists()
    }

    @Test
    fun aQueueAboutToStartSaysSo() {
        show(SeparationStatus.Idle, queued = listOf("Bê"))
        openPanel()

        composeRule.onNodeWithText("Começando…").assertExists()
    }

    @Test
    fun aFailureWithMoreInTheQueueShowsTheQueueNotTheFailure() {
        show(SeparationStatus.Failed("M", SeparationFailure.ERROR), queued = listOf("Bê"))
        openPanel()

        composeRule.onNodeWithText("Começando…").assertExists()
        composeRule.onNodeWithText("Não deu para separar a guitarra de M").assertDoesNotExist()
    }
}
