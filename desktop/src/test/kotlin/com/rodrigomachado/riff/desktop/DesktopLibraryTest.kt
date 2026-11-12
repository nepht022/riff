package com.rodrigomachado.riff.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.rodrigomachado.riff.core.data.folder.pcUri
import com.rodrigomachado.riff.desktop.pad.NoPadSource
import com.rodrigomachado.riff.feature.home.HomeTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class DesktopLibraryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private var graph: DesktopGraph? = null

    @After
    fun close() {
        graph?.close()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun soloOpensTheLibraryAndImportsAPcFolder() {
        val charts = temp.newFolder("mapas")
        File(charts, "Minha Música").apply { mkdirs() }.run {
            File(this, "notes.chart").writeText(CHART)
            File(this, "song.ogg").writeBytes(ByteArray(10))
        }
        File(charts, "quebrada").apply { mkdirs() }.run { File(this, "notes.chart").writeText("não é mapa") }
        val graph = DesktopGraph(
            temp.newFolder("dados"),
            padSource = NoPadSource,
            // Sem o ranking (desligado, como sem ranking.url): nunca o script real.
            rankingUrl = "",
            rankingTransport = OfflineRankingScript,
        ).also { graph = it }
        // A pasta já escolhida e importada (o seletor do PC é uma janela do sistema, conferida à mão).
        runBlocking {
            graph.libraryFolderStore.setRootUri(pcUri(charts))
            graph.chartImporter.import(pcUri(charts))
        }
        DesktopLocale.applyDefault()
        composeRule.setContent { RiffDesktopApp(graph) }

        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(HomeTestTags.SOLO))
        composeRule.waitUntilAtLeastOneExists(hasText("Canção do PC"), TIMEOUT_MS)
        // O Reimportar fica no menu ⋮ do topo.
        composeRule.clickOnWindowThread(composeRule.onNodeWithContentDescription("Mais opções"))
        composeRule.waitUntilAtLeastOneExists(hasText("Reimportar"), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithText("Reimportar"))

        composeRule.waitUntilAtLeastOneExists(hasText("1 mapa importado"), TIMEOUT_MS)
        composeRule.onNodeWithText("1 mapa importado").assertExists()
        composeRule.onNodeWithText("1 pasta com erro").assertExists()
    }

    /** A capa posta na pasta depois aparece ao Reimportar (o "sem imagem" guardado é esquecido ao fim). */
    @Test
    fun reimportingForgetsTheMissingCover() {
        val charts = temp.newFolder("mapas")
        val song = File(charts, "Minha Música").apply { mkdirs() }
        File(song, "notes.chart").writeText(CHART)
        val graph = DesktopGraph(
            temp.newFolder("dados"),
            padSource = NoPadSource,
            rankingUrl = "",
            rankingTransport = OfflineRankingScript,
        ).also { graph = it }
        runBlocking {
            graph.chartImporter.import(pcUri(charts))
            val id = graph.charts.observeCharts().first().single().id
            assertNull(graph.chartThumbs.load(id))

            ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", File(song, "album.png"))
            graph.chartImporter.import(pcUri(charts))

            assertNotNull(graph.chartThumbs.load(id))
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        val CHART = """
            [Song]
            {
              Name = "Canção do PC"
              Artist = "Banda"
            }
            [ExpertSingle]
            {
              192 = N 0 0
            }
        """.trimIndent()
    }
}
