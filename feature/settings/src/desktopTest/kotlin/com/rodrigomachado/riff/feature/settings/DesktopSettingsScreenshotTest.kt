package com.rodrigomachado.riff.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/**
 * As Configurações na janela deitada do PC (960×540): uma coluna de até 640 dp no meio, sem a aba da IA nem as
 * Licenças. Com até 1% dos pixels de folga (a suavização das letras muda entre Windows e Linux), como nos outros
 * screenshots do PC.
 */
class DesktopSettingsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val system = Locale.getDefault()

    @After
    fun restore() = Locale.setDefault(system)

    private fun capture(name: String, tab: SettingsTab) {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))
        composeRule.setContent {
            Box(Modifier.size(960.dp, 540.dp)) {
                RiffTheme {
                    StageBackground(Modifier.fillMaxSize()) {
                        SettingsContent(
                            settings = Settings.DEFAULT.copy(leftHanded = true, audioOffsetMs = -40),
                            onMasterVolume = {},
                            onMissVolume = {},
                            onNoteSpeed = {},
                            onLeftHanded = {},
                            onMenuSounds = {},
                            onPreviewMiss = {},
                            onCalibrate = {},
                            onBack = {},
                            initialTab = tab,
                            onLicenses = null,
                            tabs = SettingsTab.entries - SettingsTab.AI,
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/desktop/$name.png", roborazziOptions = Tolerance)
    }

    @Test
    fun generalTab() = capture("Settings_wide", SettingsTab.GENERAL)

    @Test
    fun gameTab() = capture("Settings_wide_game", SettingsTab.GAME)

    private companion object {
        val Tolerance = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))
    }
}
