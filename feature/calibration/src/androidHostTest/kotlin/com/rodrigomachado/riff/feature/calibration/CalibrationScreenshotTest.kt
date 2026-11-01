package com.rodrigomachado.riff.feature.calibration

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi")
class CalibrationScreenshotTest {
    @Test
    fun instructions() = snapshot("Calibration_instructions", CalibrationUiState.Instructions)

    @Test
    fun result() = snapshot("Calibration_result", CalibrationUiState.Result(42))

    private fun snapshot(name: String, state: CalibrationUiState) {
        captureRoboImage("src/test/screenshots/$name.png") {
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
