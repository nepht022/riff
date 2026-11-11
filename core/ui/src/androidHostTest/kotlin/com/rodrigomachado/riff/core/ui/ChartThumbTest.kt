package com.rodrigomachado.riff.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.art.ChartArtImages
import com.rodrigomachado.riff.core.ui.component.ChartThumb
import com.rodrigomachado.riff.core.ui.component.ChartThumbTestTags
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartThumbTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var images by mutableStateOf<ChartArtImages?>(null)

    private fun show(shown: ChartArtImages?) {
        images = shown
        composeRule.waitForIdle()
    }

    @Before
    fun setUp() {
        composeRule.setContent {
            RiffTheme { ChartThumb("chart-1", "Through the Fire and Flames", 56.dp, images) }
        }
    }

    @Test
    fun missingOrBrokenArtShowsInitials() {
        // Sem leitor de imagens (prévia, teste), sem imagem no mapa ou com imagem que não abre: as iniciais.
        listOf(
            null,
            ChartArtImages { null },
            ChartArtImages { error("imagem corrompida") },
        ).forEach { images ->
            show(images)
            composeRule.onNodeWithText("TF").assertExists()
            composeRule.onNodeWithTag(ChartThumbTestTags.ART).assertDoesNotExist()
        }
    }

    @Test
    fun artReplacesInitials() {
        val image = ImageBitmap(8, 8)
        show(ChartArtImages { image })
        composeRule.onNodeWithTag(ChartThumbTestTags.ART).assertExists()
        composeRule.onNodeWithText("TF").assertDoesNotExist()
    }
}
