package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.data.importer.ImportFailure
import com.rodrigomachado.riff.core.data.importer.ImportFailureKind
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
    /** A lista com o aviso da importação, as capas (as iniciais, sem imagem), os recordes e o selo da IA. */
    @Test
    fun listWithReport() = snapshot("Library_list") {
        LibraryContent(
            state = LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = LIST,
                aiSeparated = setOf("2"),
                bests = BESTS,
                report = ImportReport(
                    3,
                    listOf(
                        ImportFailure(
                            "Quebrada",
                            ImportFailureKind.INVALID_CHART,
                            "linha 3: nota inválida: abc = N 0 0",
                        ),
                    ),
                ),
            ),
            onPickFolder = {},
            onRescan = {},
            onReportDismissed = {},
            onPlay = { _, _ -> },
            onBack = {},
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
            onBack = {},
        )
    }

    @Test
    fun aiSeparatedAndThePrompt() = snapshot("Library_ai_separation") {
        LibraryContent(
            state = LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = listOf(
                    chart("1", "Smells Like Teen Spirit", "Nirvana", setOf(Difficulty.EXPERT)),
                    chart("2", "Enter Sandman", "Metallica", setOf(Difficulty.HARD, Difficulty.EXPERT)),
                ),
                aiSeparated = setOf("1"),
                prompt = SeparationAsk("2", "Enter Sandman", Difficulty.EXPERT, lowMemory = false),
            ),
            onPickFolder = {},
            onRescan = {},
            onReportDismissed = {},
            onPlay = { _, _ -> },
            onBack = {},
        )
    }

    @Test
    fun thePromptWithOneAlreadySeparating() = snapshot("Library_ai_queue") {
        LibraryContent(
            state = LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = listOf(chart("2", "Enter Sandman", "Metallica", setOf(Difficulty.HARD, Difficulty.EXPERT))),
                prompt = SeparationAsk("2", "Enter Sandman", Difficulty.EXPERT, lowMemory = false, busy = true),
            ),
            onPickFolder = {},
            onRescan = {},
            onReportDismissed = {},
            onPlay = { _, _ -> },
            onBack = {},
        )
    }

    @Test
    fun aiSeparatedBadge() = snapshot("Library_ai_badge") {
        LibraryContent(
            state = LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = listOf(chart("1", "Smells Like Teen Spirit", "Nirvana", setOf(Difficulty.EXPERT))),
                aiSeparated = setOf("1"),
            ),
            onPickFolder = {},
            onRescan = {},
            onReportDismissed = {},
            onPlay = { _, _ -> },
            onBack = {},
        )
    }

    /** Sem o aviso: os chips e os cartões logo abaixo do filtro. */
    @Test
    fun listWithRecords() = snapshot("Library_records") {
        LibraryContent(
            state = LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = LIST,
                aiSeparated = setOf("2"),
                bests = BESTS,
            ),
            onPickFolder = {},
            onRescan = {},
            onReportDismissed = {},
            onPlay = { _, _ -> },
            onBack = {},
        )
    }

    /** A pasta sem mapas. */
    @Test
    fun emptyLibrary() = snapshot("Library_empty") {
        LibraryContent(
            LibraryUiState(isLoading = false, hasFolder = true),
            onPickFolder = {},
            onRescan = {},
            onReportDismissed = {},
            onPlay = { _, _ -> },
            onBack = {},
        )
    }

    /** Fonte do sistema em 200%: a lista rola, nada cortado no que aparece. */
    @Test
    @Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi", fontScale = 2f)
    fun withALargeFont() = snapshot("LargeFont_library") {
        LibraryContent(
            state = LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = LIST,
                aiSeparated = setOf("2"),
                bests = BESTS,
            ),
            onPickFolder = {},
            onRescan = {},
            onReportDismissed = {},
            onPlay = { _, _ -> },
            onBack = {},
        )
    }

    /** A escolha de música da sala: "Escolher música", sem "+ Mapas". */
    @Test
    fun pickForTheRoom() = snapshot("Library_pick") {
        LibraryContent(
            state = LibraryUiState(isLoading = false, hasFolder = true, charts = LIST, bests = BESTS),
            onPickFolder = {},
            onRescan = {},
            onReportDismissed = {},
            onPlay = { _, _ -> },
            onBack = {},
            pickForRoom = true,
        )
    }

    @Test
    fun selectingToDelete() = snapshot("Library_selecting") {
        LibraryContent(
            state = LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = listOf(
                    chart("1", "Through the Fire and Flames", "DragonForce", setOf(Difficulty.HARD, Difficulty.EXPERT)),
                    chart("2", "Bohemian Rhapsody", "Queen", setOf(Difficulty.EASY, Difficulty.MEDIUM)),
                    chart("3", "Smells Like Teen Spirit", "Nirvana", setOf(Difficulty.EXPERT)),
                ),
                deletion = DeletionState(selecting = true, selected = setOf("1", "3")),
                separableSelected = 2,
            ),
            onPickFolder = {},
            onRescan = {},
            onReportDismissed = {},
            onPlay = { _, _ -> },
            onBack = {},
        )
    }

    private fun snapshot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png") {
            RiffTheme { StageBackground(Modifier.fillMaxSize()) { content() } }
        }
    }

    companion object {
        val LIST = listOf(
            chart("1", "Through the Fire and Flames", "DragonForce", setOf(Difficulty.HARD, Difficulty.EXPERT))
                .copy(durationMs = 441_000),
            chart("2", "Smells Like Teen Spirit", "Nirvana", setOf(Difficulty.EXPERT)).copy(durationMs = 301_000),
            chart("3", "Bohemian Rhapsody", "Queen", setOf(Difficulty.EASY, Difficulty.MEDIUM))
                .copy(durationMs = 355_000, separateGuitar = true),
            chart("4", "Pasta Sem Nome", "", setOf(Difficulty.EASY)),
        )
        val BESTS = mapOf(
            "1" to ChartBest(412_380, Difficulty.EXPERT),
            "3" to ChartBest(98_150, Difficulty.MEDIUM),
        )
    }
}
