package com.rodrigomachado.riff.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.rodrigomachado.riff.core.model.Settings
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
 * As Configurações na janela deitada do PC (960×540), como no app: as abas numa coluna à esquerda (com "Licenças e
 * privacidade" no pé), as opções em blocos à direita e a barra de teclas no rodapé (sem controle ligado: só o
 * teclado). Com até 1% dos pixels de folga (a suavização das letras muda entre Windows e Linux), como nos outros
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
            CompositionLocalProvider(LocalGamepad provides UnpluggedPad) {
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
                                tabs = SettingsTab.entries,
                            )
                        }
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

    @Test
    fun controlsTab() = capture("Settings_wide_controls", SettingsTab.CONTROLS)

    /** O controle do PC desligado: a barra de teclas aparece só com o teclado. */
    private object UnpluggedPad : Gamepad {
        override val events = MutableSharedFlow<PadEvent>()
        override val connected = MutableStateFlow(false)
        override val presses = MutableSharedFlow<String>()
    }

    private companion object {
        val Tolerance = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))
    }
}
