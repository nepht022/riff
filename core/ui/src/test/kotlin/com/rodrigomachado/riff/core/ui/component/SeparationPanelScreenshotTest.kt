package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.rodrigomachado.riff.core.model.QueuedSeparation
import com.rodrigomachado.riff.core.model.SeparationStatus
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** O painel do ícone da palheta é um diálogo: a captura é da tela inteira, com ele aberto. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi")
class SeparationPanelScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theQueue() {
        composeRule.setContent {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    SeparationIndicatorContent(
                        SeparationStatus.Running("1", "Smells Like Teen Spirit", 0.42f, remainingMs = 110_000),
                        onCancel = {},
                        onDismissFailure = {},
                        queue = SeparationQueueView(
                            listOf(
                                QueuedSeparation("2", "Enter Sandman"),
                                QueuedSeparation("3", "Through the Fire and Flames"),
                            ),
                        ),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(SeparationIndicatorTags.ICON).performClick()
        composeRule.waitForIdle()

        captureScreenRoboImage("src/test/screenshots/Separation_queue.png")
    }

    @Test
    fun theIconWithTheCount() {
        composeRule.setContent {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    SeparationIndicatorContent(
                        SeparationStatus.Running("1", "M", 0.42f, null),
                        onCancel = {},
                        onDismissFailure = {},
                        queue = SeparationQueueView(listOf(QueuedSeparation("2", "B"), QueuedSeparation("3", "C"))),
                    )
                }
            }
        }

        captureScreenRoboImage("src/test/screenshots/Separation_icon_count.png")
    }
}
