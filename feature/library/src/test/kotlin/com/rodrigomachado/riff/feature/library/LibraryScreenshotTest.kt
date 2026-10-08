package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.data.importer.ImportFailure
import com.rodrigomachado.riff.core.data.importer.ImportReport
import com.rodrigomachado.riff.core.model.Difficulty
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
class LibraryScreenshotTest {
    @Test
    fun listWithReport() = snapshot("Library_list") {
        LibraryContent(
            state = LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = listOf(
                    chart("1", "Through the Fire and Flames", "DragonForce", setOf(Difficulty.HARD, Difficulty.EXPERT)),
                    chart("2", "Bohemian Rhapsody", "Queen", setOf(Difficulty.EASY, Difficulty.MEDIUM)),
                    chart("3", "Pasta Sem Nome", "", setOf(Difficulty.EASY)),
                ),
                report = ImportReport(3, listOf(ImportFailure("Quebrada", "linha 3: nota inválida: abc = N 0 0"))),
            ),
            onPickFolder = {},
            onRescan = {},
            onReportDismissed = {},
            onPlay = { _, _ -> },
        )
    }

    @Test
    fun noFolder() = snapshot("Library_no_folder") {
        LibraryContent(
            LibraryUiState(isLoading = false),
            onPickFolder = {},
            onRescan = {},
            onReportDismissed = {},
            onPlay = { _, _ -> },
        )
    }

    private fun snapshot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png") {
            RiffTheme { StageBackground(Modifier.fillMaxSize()) { content() } }
        }
    }
}
