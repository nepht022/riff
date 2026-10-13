package com.rodrigomachado.riff.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.model.Settings
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
class SettingsScreenshotTest {
    @Test
    fun settings() {
        captureRoboImage("src/test/screenshots/Settings.png") {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    SettingsContent(
                        settings = Settings.DEFAULT.copy(hitVolume = 0.3f, leftHanded = true),
                        onMasterVolume = {},
                        onHitVolume = {},
                        onMissVolume = {},
                        onNoteSpeed = {},
                        onLeftHanded = {},
                        onMenuSounds = {},
                        onPreviewHit = {},
                        onPreviewMiss = {},
                        onCalibrate = {},
                        onBack = {},
                    )
                }
            }
        }
    }
}
