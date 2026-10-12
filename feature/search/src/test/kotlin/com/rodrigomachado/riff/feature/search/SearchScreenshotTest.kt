package com.rodrigomachado.riff.feature.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.encore.EncoreChart
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
class SearchScreenshotTest {
    private fun chart(md5: String, name: String, artist: String, charter: String, difficulties: Set<Difficulty>) =
        EncoreChart(md5, name, artist, charter, 302_667, difficulties, hasVideoBackground = false)

    @Test
    fun results() = snapshot("Search_results") {
        SearchContent(
            state = SearchUiState(
                query = "nirvana",
                results = listOf(
                    ResultItem(
                        chart("a", "Smells Like Teen Spirit", "Nirvana", "highfine", setOf(Difficulty.EXPERT)),
                        ItemState.Available,
                        separateGuitar = true,
                    ),
                    ResultItem(
                        chart("b", "Lithium", "Nirvana", "metalli3212", Difficulty.entries.toSet()),
                        ItemState.Downloading(0.4f),
                        separateGuitar = null,
                    ),
                    ResultItem(
                        chart("c", "In Bloom", "Nirvana", "highfine", setOf(Difficulty.HARD, Difficulty.EXPERT)),
                        ItemState.Ready("c", setOf(Difficulty.HARD, Difficulty.EXPERT)),
                        separateGuitar = false,
                    ),
                ),
                hasMore = true,
            ),
            onQueryChange = {},
            onSearch = {},
            onLoadMore = {},
            onDownload = {},
            onPlay = { _, _ -> },
            onAllowWrite = {},
            onBack = {},
        )
    }

    @Test
    fun withoutWriteAccess() = snapshot("Search_write_needed") {
        SearchContent(
            state = SearchUiState(query = "", canWrite = false),
            onQueryChange = {},
            onSearch = {},
            onLoadMore = {},
            onDownload = {},
            onPlay = { _, _ -> },
            onAllowWrite = {},
            onBack = {},
        )
    }

    private fun snapshot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png") {
            RiffTheme { StageBackground(Modifier.fillMaxSize()) { content() } }
        }
    }
}
