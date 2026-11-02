package com.rodrigomachado.riff.feature.calibration

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyPress
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * No PC a calibração se mede pelo teclado: qualquer tecla é um toque (menos o Esc), a repetição automática de uma
 * tecla segurada não conta de novo, e o Alt+Tab no meio da medição volta às instruções.
 */
// O evento de tecla avulso (sem o estado de teclas do performKeyInput): duas descidas seguidas, como a repetição.
@OptIn(InternalComposeUiApi::class)
class DesktopCalibrationKeysTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val taps = mutableListOf<Long>()

    private fun showMeasuring() {
        composeRule.setContent {
            RiffTheme {
                CalibrationContent(
                    state = CalibrationUiState.Measuring(taps = 0),
                    onStart = {},
                    onTap = { taps += it },
                    onNudge = {},
                    onSave = {},
                    onDone = {},
                    onReset = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun keyDown(key: Key) = composeRule.onRoot().performKeyPress(KeyEvent(key, KeyEventType.KeyDown))

    @Test
    fun anyKeyCountsAsATap() {
        showMeasuring()

        keyDown(Key.Spacebar)
        keyDown(Key.A)

        assertEquals(2, taps.size)
    }

    @Test
    fun holdingAKeyCountsOnce() {
        showMeasuring()

        keyDown(Key.A)
        keyDown(Key.A) // a repetição automática do Windows: outra descida, sem a subida antes

        assertEquals(1, taps.size)
    }

    @Test
    fun escapeIsNotATap() {
        showMeasuring()

        keyDown(Key.Escape)

        assertEquals(0, taps.size)
    }

    @Test
    fun losingTheWindowFocusStopsTheMeasuring() {
        val viewModel = CalibrationViewModel(FakeSettings(), ClickTrackPlayer { awaitCancellation() })
        lateinit var window: ControlledWindowInfo
        composeRule.setContent {
            val real = LocalWindowInfo.current
            window = remember { ControlledWindowInfo(real) }
            CompositionLocalProvider(LocalWindowInfo provides window) {
                RiffTheme { CalibrationScreen(onDone = {}, viewModel = viewModel) }
            }
        }
        composeRule.runOnIdle { viewModel.start() }
        composeRule.waitForIdle()
        assertEquals(CalibrationUiState.Measuring(taps = 0), viewModel.uiState.value)

        window.isWindowFocused = false
        composeRule.waitForIdle()

        assertEquals(CalibrationUiState.Instructions, viewModel.uiState.value)
    }
}

/** A janela do teste com o foco nas mãos do teste (o Alt+Tab de mentira). */
private class ControlledWindowInfo(real: WindowInfo) : WindowInfo by real {
    override var isWindowFocused by mutableStateOf(true)
}

private class FakeSettings : SettingsRepository {
    override val settings = MutableStateFlow(Settings.DEFAULT)

    override suspend fun update(transform: (Settings) -> Settings) {
        settings.value = transform(settings.value)
    }
}
