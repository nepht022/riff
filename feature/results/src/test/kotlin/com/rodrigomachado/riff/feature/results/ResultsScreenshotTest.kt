package com.rodrigomachado.riff.feature.results

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
class ResultsScreenshotTest {
    @Test
    fun newBest() {
        captureRoboImage("src/test/screenshots/Results_new_best.png") {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    ResultsContent(
                        ResultsUiState.Ready(
                            record(2, 48_300),
                            "Through the Fire and Flames",
                            "DragonForce",
                            isNewBest = true,
                        ),
                        onPlayAgain = { _, _ -> },
                        onBackToLibrary = {},
                    )
                }
            }
        }
    }
}
