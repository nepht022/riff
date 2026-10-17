package com.rodrigomachado.riff.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.model.SeparationUsage
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.model.TrackLook
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.core.ui.theme.Spacing
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi")
class SettingsScreenshotTest {

    /** Uma imagem por aba; a tela abre em Geral. */
    @Test
    fun settings() = tab("Settings", SettingsTab.GENERAL)

    @Test
    fun soundsTab() = tab("Settings_sounds", SettingsTab.SOUNDS)

    @Test
    fun gameTab() = tab("Settings_game", SettingsTab.GAME)

    @Test
    fun trackLookTab() = tab("Settings_track_look_tab", SettingsTab.TRACK_LOOK)

    @Test
    fun aiTab() = tab("Settings_ai", SettingsTab.AI)

    @Test
    @Config(qualifiers = "en-w360dp-h640dp-xhdpi")
    fun settingsInEnglish() = tab("Settings_en", SettingsTab.GENERAL)

    private fun tab(name: String, tab: SettingsTab) {
        captureRoboImage("src/test/screenshots/$name.png") {
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
                        separationUsage = SeparationUsage(3, 21_000_000),
                        initialTab = tab,
                    )
                }
            }
        }
    }

    /** As quatro listas de escolha do visual, com as miniaturas desenhadas pelo app. */
    @Test
    @Config(qualifiers = "pt-rBR-w1240dp-h760dp-mdpi")
    fun trackLookChoices() {
        captureRoboImage("src/test/screenshots/Settings_track_look.png") {
            RiffTheme {
                // As cores do conteúdo de um diálogo do app.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        for (choice in LookChoice.entries) {
                            Column(Modifier.weight(1f)) {
                                TrackLookChoices(choice, TrackLook.DEFAULT) {}
                            }
                        }
                    }
                }
            }
        }
    }
}
