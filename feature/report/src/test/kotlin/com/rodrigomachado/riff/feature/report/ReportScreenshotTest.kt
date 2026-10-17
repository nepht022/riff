package com.rodrigomachado.riff.feature.report

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
class ReportScreenshotTest {
    private val device = DeviceInfo("1.4", "16", "samsung SM-S931B", "pt-BR")

    @Test
    fun form() = capture(
        "Report",
        ReportUiState(text = "A música trava no refrão de Teen Spirit.", contact = "eu@exemplo.com", device = device),
    )

    @Test
    fun failed() = capture(
        "Report_failed",
        ReportUiState(text = "Trava", device = device, status = ReportStatus.FAILED),
    )

    @Test
    fun sent() = capture("Report_sent", ReportUiState(text = "Trava", device = device, status = ReportStatus.SENT))

    private fun capture(name: String, state: ReportUiState) {
        captureRoboImage("src/test/screenshots/$name.png") {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    ReportContent(state, {}, {}, {}, {}, {}, {})
                }
            }
        }
    }
}
