package com.rodrigomachado.riff.feature.ranking

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
class RankingScreenshotTest {
    @Test
    fun namePrompt() = snapshot("Ranking_name_prompt") {
        PlayerNameDialog(PlayerNameUiState(name = "Rodrigo"), onName = {}, onSave = {}, onLater = {})
    }

    @Test
    fun linkCode() = snapshot("Ranking_link_code") {
        PlayerNameDialog(
            PlayerNameUiState(
                name = "Rodrigo",
                hasName = true,
                step = NameStep.SHOW_CODE,
                linkCode = "K7QM4X",
                secondsLeft = 545,
            ),
            onName = {},
            onSave = {},
            onLater = null,
        )
    }

    @Test
    fun linkEnter() = snapshot("Ranking_link_enter") {
        PlayerNameDialog(
            PlayerNameUiState(
                name = "Rodrigo",
                hasName = true,
                step = NameStep.ENTER_CODE,
                code = "k7qm4x",
                linkError = com.rodrigomachado.riff.core.data.ranking.LinkResult.InvalidCode,
            ),
            onName = {},
            onSave = {},
            onLater = {},
        )
    }

    @Test
    fun songs() = snapshot("Ranking_songs") {
        RankingSongsContent(
            RankingSongsUiState(
                songs = listOf(
                    com.rodrigomachado.riff.core.data.ranking.RankedSong(
                        "m1",
                        "Through the Fire and Flames",
                        "DragonForce",
                        27,
                        3,
                    ),
                    com.rodrigomachado.riff.core.data.ranking.RankedSong(
                        "m2",
                        "Smells Like Teen Spirit",
                        "Nirvana",
                        12,
                        1,
                    ),
                    com.rodrigomachado.riff.core.data.ranking.RankedSong("m3", "Bohemian Rhapsody", "Queen", 8, null),
                ),
            ),
            onQuery = {},
            onSong = {},
            onRetry = {},
            onBack = {},
        )
    }

    @Test
    fun board() = snapshot("Ranking_board") {
        val lines = listOf("Ana" to 61_200.0, "Bruno_88" to 55_900.0, "Rodrigo" to 48_300.0, "Carla" to 47_050.0)
            .mapIndexed { i, (name, value) ->
                com.rodrigomachado.riff.core.data.ranking.BoardLine(i + 1, name, value, you = name == "Rodrigo")
            }
        RankingBoardContent(
            RankingBoardUiState(
                title = "Through the Fire and Flames",
                difficulty = com.rodrigomachado.riff.core.model.Difficulty.HARD,
                board = com.rodrigomachado.riff.core.data.ranking.Board(
                    com.rodrigomachado.riff.core.model.Difficulty.entries,
                    com.rodrigomachado.riff.core.model.Difficulty.HARD,
                    27,
                    lines,
                ),
            ),
            BoardActions(),
        )
    }

    private fun snapshot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png") {
            RiffTheme { StageBackground(Modifier.fillMaxSize()) { content() } }
        }
    }
}
