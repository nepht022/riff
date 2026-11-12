package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** O menu ⋮ abre num popup: a captura é da tela inteira, com ele aberto. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi")
class LibraryMenuScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theMenu() {
        composeRule.setContent {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    LibraryContent(
                        state = LibraryUiState(
                            isLoading = false,
                            hasFolder = true,
                            charts = LibraryScreenshotTest.LIST,
                            bests = LibraryScreenshotTest.BESTS,
                        ),
                        onPickFolder = {},
                        onRescan = {},
                        onReportDismissed = {},
                        onPlay = { _, _ -> },
                        onBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Mais opções").performClick()
        composeRule.waitForIdle()

        captureScreenRoboImage("src/test/screenshots/Library_menu.png")
    }
}
