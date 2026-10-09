package com.rodrigomachado.riff.feature.calibration

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class CalibrationContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(state: CalibrationUiState) = composeRule.setContent {
        RiffTheme {
            CalibrationContent(
                state = state,
                onStart = { events += "start" },
                onTap = { events += "tap" },
                onNudge = { events += "nudge:$it" },
                onSave = { events += "save" },
                onDone = { events += "done" },
            )
        }
    }

    @Test
    fun theInstructionsExplainWhatToDo() {
        show(CalibrationUiState.Instructions)

        composeRule.onNodeWithText("Toque na tela junto com cada clique.").assertExists()
        composeRule.onNodeWithText("Começar").performClick()

        assertEquals(listOf("start"), events)
    }

    @Test
    fun whileMeasuringTheWholeScreenTakesTaps() {
        show(CalibrationUiState.Measuring(taps = 3))

        composeRule.onNodeWithText("3 toques").assertExists()
        composeRule.onNodeWithTag(CalibrationTestTags.TAP_AREA).performTouchInput { down(center) }

        assertEquals(listOf("tap"), events)
    }

    @Test
    fun theResultCanBeNudgedSavedOrRepeated() {
        show(CalibrationUiState.Result(42))

        composeRule.onNodeWithText("Atraso medido: 42 ms").assertExists()
        composeRule.onNodeWithText("−5 ms").performClick()
        composeRule.onNodeWithText("+5 ms").performClick()
        composeRule.onNodeWithText("Salvar").performClick()
        composeRule.onNodeWithText("Repetir").performClick()

        assertEquals(listOf("nudge:-5", "nudge:5", "save", "start"), events)
    }

    @Test
    fun anUnsteadyMeasurementAsksToTryAgain() {
        show(CalibrationUiState.Unstable)

        composeRule.onNodeWithText("Medição instável. Tente de novo, tocando junto com o clique.").assertExists()
        composeRule.onNodeWithText("Repetir").performClick()

        assertEquals(listOf("start"), events)
    }

    @Test
    fun anOutputThatDoesNotOpenIsExplained() {
        show(CalibrationUiState.AudioFailed)

        composeRule.onNodeWithText("Não deu para tocar o metrônomo.").assertExists()
    }
}
