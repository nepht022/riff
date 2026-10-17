package com.rodrigomachado.riff.feature.nowplaying

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.model.MatchStatus
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
class NowPlayingScreenshotTest {
    @Test
    fun matched() = snapshot("NowPlaying_matched") {
        content(NowPlayingUiState.Playing(playing(MatchStatus.Matched(chart(), 0))))
    }

    @Test
    fun suggested() = snapshot("NowPlaying_suggested") {
        content(NowPlayingUiState.Playing(playing(MatchStatus.Suggested(chart(), 0.9f))))
    }

    @Test
    fun noMatch() = snapshot("NowPlaying_no_match") {
        content(NowPlayingUiState.Playing(playing(MatchStatus.NoMatch)))
    }

    @Composable
    private fun content(state: NowPlayingUiState) = NowPlayingContent(
        state = state,
        onRetry = {},
        onPlay = {},
        onConfirm = { _, _, _ -> },
        onChooseChart = { _, _ -> },
        onOpenLibrary = {},
    )

    private fun snapshot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png") {
            // Relógio parado no instante da leitura da música: a barra de tempo sai igual toda vez.
            CompositionLocalProvider(LocalElapsedClock provides { 0L }) {
                RiffTheme { StageBackground(Modifier.fillMaxSize()) { content() } }
            }
        }
    }
}
