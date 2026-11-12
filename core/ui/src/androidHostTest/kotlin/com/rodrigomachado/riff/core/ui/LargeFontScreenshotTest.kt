package com.rodrigomachado.riff.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.ui.component.BackRow
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.core.ui.theme.Spacing
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Fonte do sistema em 200%: letreiro e botão crescem sem cortar texto. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi", fontScale = 2.0f)
class LargeFontScreenshotTest {
    @Test
    fun posterTitleAndButtonAtDoubleFontScale() {
        captureRoboImage("src/test/screenshots/LargeFont_title_button.png") {
            RiffTheme {
                StageBackground {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        PosterTitle("RIFF")
                        PosterButton("Importar pasta", onClick = {})
                    }
                }
            }
        }
    }

    @Test
    fun backRowAtDoubleFontScale() {
        captureRoboImage("src/test/screenshots/LargeFont_back_row.png") {
            RiffTheme {
                StageBackground {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        BackRow(onBack = {})
                        BackRow(text = "Sair da sala", onBack = {})
                    }
                }
            }
        }
    }
}
