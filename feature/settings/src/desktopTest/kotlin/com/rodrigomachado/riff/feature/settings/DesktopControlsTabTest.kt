package com.rodrigomachado.riff.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import com.rodrigomachado.riff.core.model.Controls
import com.rodrigomachado.riff.core.ui.input.Gamepad
import com.rodrigomachado.riff.core.ui.input.LocalGamepad
import com.rodrigomachado.riff.core.ui.input.PadEvent
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

private class FakeGamepad : Gamepad {
    override val events = MutableSharedFlow<PadEvent>(extraBufferCapacity = 16)
    override val connected = MutableStateFlow(true)
    override val presses = MutableSharedFlow<String>(extraBufferCapacity = 16)
}

/** A janela do teste com o foco nas mãos do teste (o Alt+Tab de mentira). */
private class ControlledWindowInfo(real: WindowInfo) : WindowInfo by real {
    override var isWindowFocused by mutableStateOf(true)
}

/** A aba Controles do PC: cada ação com a tecla e o botão, e a escolha apertando a tecla ou o botão. */
class DesktopControlsTabTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val system = Locale.getDefault()
    private val pad = FakeGamepad()
    private val events = mutableListOf<String>()
    private lateinit var window: ControlledWindowInfo

    @After
    fun restore() = Locale.setDefault(system)

    private fun show(controls: Controls = Controls(), padConnected: Boolean = true) {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))
        composeRule.setContent {
            val real = LocalWindowInfo.current
            window = remember { ControlledWindowInfo(real) }
            CompositionLocalProvider(LocalWindowInfo provides window, LocalGamepad provides pad) {
                RiffTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        ControlsTab(
                            controls = controls,
                            padConnected = padConnected,
                            onStrum = { events += "strum:$it" },
                            onKey = { action, key -> events += "key:$action:$key" },
                            onPad = { action, button -> events += "pad:$action:$button" },
                            onDefaults = { events += "defaults" },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** A primeira linha "Verde" é a do teclado; a última, a do controle. */
    private fun chooseKeyForGreen() {
        composeRule.onAllNodesWithText("Verde").onFirst().performClick()
        composeRule.onNodeWithText("Aperte a tecla para Verde").assertExists()
    }

    private fun chooseButtonForGreen() {
        composeRule.onAllNodesWithText("Verde").onLast().performScrollTo().performClick()
        composeRule.onNodeWithText("Aperte o botão para Verde").assertExists()
    }

    /** Na thread da janela, como o teclado de verdade. */
    private fun press(key: Key) {
        composeRule.runOnUiThread {
            composeRule.onNodeWithTag(ControlsTestTags.CHOOSING).performKeyInput { pressKey(key) }
        }
        composeRule.waitForIdle()
    }

    private fun padPress(button: String) {
        composeRule.runOnIdle { assertTrue(pad.presses.tryEmit(button)) }
        composeRule.waitForIdle()
    }

    @Test
    fun theControlsTabShowsEachActionWithItsKeyAndButton() {
        show()

        composeRule.onNodeWithText("Teclado").assertExists()
        composeRule.onNodeWithText("Controle").assertExists()
        composeRule.onNodeWithText("A").assertExists()
        composeRule.onNodeWithText("△").assertExists()
        assertEquals(2, composeRule.onAllNodesWithText("Verde").fetchSemanticsNodes().size)
        assertEquals(2, composeRule.onAllNodesWithText("Palheta ↓").fetchSemanticsNodes().size)
    }

    @Test
    fun pressingAKeyInTheDialogChoosesIt() {
        show()
        chooseKeyForGreen()

        press(Key.Q)

        assertEquals(listOf("key:FRET_1:${Key.Q.keyCode}"), events)
        composeRule.onNodeWithText("Aperte a tecla para Verde").assertDoesNotExist()
    }

    @Test
    fun escapeCancelsTheChoice() {
        show()
        chooseKeyForGreen()

        press(Key.Escape)

        assertEquals(emptyList<String>(), events)
        composeRule.onNodeWithText("Aperte a tecla para Verde").assertDoesNotExist()
    }

    @Test
    fun pressingAPadButtonChoosesIt() {
        show()
        chooseButtonForGreen()

        padPress("r:9")

        assertEquals(listOf("pad:FRET_1:r:9"), events)
        composeRule.onNodeWithText("Aperte o botão para Verde").assertDoesNotExist()
    }

    /** Alt+Tab com o diálogo aberto: o controle (lido mesmo sem foco) e as teclas não escolhem nada. */
    @Test
    fun losingTheWindowFocusChoosesNothing() {
        show()
        chooseButtonForGreen()
        composeRule.runOnIdle { window.isWindowFocused = false }

        padPress("m:0")
        press(Key.Q)

        assertEquals(emptyList<String>(), events)
        composeRule.onNodeWithText("Aperte o botão para Verde").assertExists()
        composeRule.runOnIdle { window.isWindowFocused = true }
        padPress("m:1")
        assertEquals(listOf("pad:FRET_1:m:1"), events)
    }

    /** No diálogo do teclado, o controle não escolhe; no do controle, a tecla não escolhe. */
    @Test
    fun eachDialogListensOnlyToItsDevice() {
        show()
        chooseKeyForGreen()
        padPress("m:0")
        press(Key.Escape)
        chooseButtonForGreen()
        press(Key.Q)

        assertEquals(emptyList<String>(), events)
        composeRule.onNodeWithText("Aperte o botão para Verde").assertExists()
    }

    /** Sem controle ligado o título avisa, e as linhas do controle continuam escolhíveis. */
    @Test
    fun withoutAPadTheTitleSaysSo() {
        show(padConnected = false)

        composeRule.onNodeWithText("Sem controle ligado").assertExists()
        chooseButtonForGreen()
    }

    @Test
    fun theStrumSwitchTurnsStrumOff() {
        show()

        composeRule.onNodeWithTag(ControlsTestTags.STRUM).performClick()

        assertEquals(listOf("strum:false"), events)
    }

    @Test
    fun theDefaultsButtonAsksForThem() {
        show()

        composeRule.onNodeWithText("Voltar ao padrão").performScrollTo().performClick()

        assertEquals(listOf("defaults"), events)
    }
}
