package com.rodrigomachado.riff.feature.licenses

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
class LicensesScreenshotTest {
    @Test
    fun licenses() = captureRoboImage("src/test/screenshots/Licenses.png") {
        RiffTheme {
            StageBackground(Modifier.fillMaxSize()) {
                LicensesContent(
                    LicensesUiState(
                        content = listOf(LicenseEntry("Demucs v4 (htdemucs_6s)", "Meta Platforms", "MIT", "MIT")),
                        libraries = listOf(LicenseEntry("Room", "The Android Open Source Project", "Apache 2.0", null)),
                    ),
                    onBack = {},
                )
            }
        }
    }
}
