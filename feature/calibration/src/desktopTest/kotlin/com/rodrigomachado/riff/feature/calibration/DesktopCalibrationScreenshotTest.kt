package com.rodrigomachado.riff.feature.calibration

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.input.Gamepad
import com.rodrigomachado.riff.core.ui.input.LocalGamepad
import com.rodrigomachado.riff.core.ui.input.PadEvent
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import io.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/**
 * A calibração na janela deitada do PC (960×540): uma coluna de até 640 dp no meio, com as instruções do teclado, e
 * a barra de teclas no rodapé (sem controle ligado: só o teclado; medindo, "qualquer tecla" e Esc para). Com até
 * 1% dos pixels de folga (a suavização das letras muda entre Windows e Linux), como nos outros screenshots do PC.
 */
class DesktopCalibrationScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val system = Locale.getDefault()

    @After
    fun restore() = Locale.setDefault(system)

    private fun capture(name: String, state: CalibrationUiState) {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))
        composeRule.setContent {
            CompositionLocalProvider(LocalGamepad provides UnpluggedPad) {
                Box(Modifier.size(960.dp, 540.dp)) {
                    RiffTheme {
                        StageBackground(Modifier.fillMaxSize()) {
                            CalibrationContent(
                                state,
                                onStart = {},
                                onTap = {},
                                onNudge = {},
                                onSave = {},
                                onDone = {},
                                onReset = {},
                            )
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/desktop/$name.png", roborazziOptions = Tolerance)
    }

    @Test
    fun instructions() = capture("Calibration_wide_instructions", CalibrationUiState.Instructions)

    @Test
    fun result() = capture("Calibration_wide_result", CalibrationUiState.Result(42))

    @Test
    fun measuring() = capture("Calibration_wide_measuring", CalibrationUiState.Measuring(3))

    /** O controle do PC desligado. */
    private object UnpluggedPad : Gamepad {
        override val events = MutableSharedFlow<PadEvent>()
        override val connected = MutableStateFlow(false)
        override val presses = MutableSharedFlow<String>()
    }

    private companion object {
        val Tolerance = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))
    }
}
