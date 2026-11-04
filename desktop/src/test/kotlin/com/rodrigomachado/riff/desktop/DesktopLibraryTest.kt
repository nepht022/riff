package com.rodrigomachado.riff.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.rodrigomachado.riff.core.data.folder.pcUri
import com.rodrigomachado.riff.desktop.pad.NoPadSource
import com.rodrigomachado.riff.feature.home.HomeTestTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

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
        composeRule.clickOnWindowThread(composeRule.onNodeWithText("Reimportar"))

        composeRule.waitUntilAtLeastOneExists(hasText("1 mapa importado"), TIMEOUT_MS)
        composeRule.onNodeWithText("1 mapa importado").assertExists()
        composeRule.onNodeWithText("1 pasta com erro").assertExists()
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
