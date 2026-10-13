package com.rodrigomachado.riff.feature.search

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.rodrigomachado.riff.core.data.download.ChartDownloadException
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.DifficultyDialogTestTags
import com.rodrigomachado.riff.core.ui.component.LocalMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR-w411dp-h1200dp")
class SearchContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val sounds = mutableListOf<MenuSound>()
    private val events = mutableListOf<String>()

    private fun chart(md5: String, name: String) =
        EncoreChart(md5, name, "Nirvana", "highfine", 302_667, setOf(Difficulty.EXPERT), hasVideoBackground = false)

    private fun show(state: SearchUiState) = composeRule.setContent {
        CompositionLocalProvider(LocalMenuSound provides { sounds += it }) {
            RiffTheme {
                SearchContent(
                    state = state,
                    onQueryChange = { events += "query:$it" },
                    onSearch = { events += "search" },
                    onLoadMore = { events += "more" },
                    onDownload = { events += "download:${it.md5}" },
                    onPlay = { id, difficulty -> events += "play:$id:$difficulty" },
                    onAllowWrite = { events += "allow" },
                    onBack = { events += "back" },
                    onShowVersions = { events += "versions:$it" },
                    onOnlySeparateGuitar = { events += "guitar:$it" },
                    onSearchMoreWithGuitar = { events += "more-guitar" },
                )
            }
        }
    }

    @Test
    fun eachResultShowsItsDetailsAndTheActionForItsState() {
        show(
            SearchUiState(
                query = "teen spirit",
                results = listOf(
                    ResultItem(chart("a", "Smells Like Teen Spirit"), ItemState.Available, separateGuitar = true),
                    ResultItem(
                        chart("b", "Lithium"),
                        ItemState.Ready("id-b", setOf(Difficulty.EXPERT)),
                        separateGuitar = null,
                    ),
                ),
            ),
        )

        composeRule.onAllNodesWithText("Nirvana · highfine · 5:02").assertCountEquals(2)
        composeRule.onNodeWithText("GUITARRA SEPARADA").assertExists()
        composeRule.onNodeWithText("Baixar").performClick()
        composeRule.onNodeWithText("Já na biblioteca").assertExists()
        composeRule.onNodeWithText("Jogar").performScrollTo().performClick()
        composeRule.onNodeWithTag(DifficultyDialogTestTags.OPTION).performClick()

        assertEquals(listOf("download:a", "play:id-b:EXPERT"), events)
        assertEquals(listOf(MenuSound.CONFIRM, MenuSound.CONFIRM, MenuSound.CONFIRM), sounds)
    }

    @Test
    fun loadingMoreNavigates() {
        show(
            SearchUiState(
                query = "teen",
                results = listOf(ResultItem(chart("a", "A"), ItemState.Available, null)),
                hasMore = true,
            ),
        )

        composeRule.onNodeWithText("Carregar mais").performScrollTo().performClick()

        assertEquals(listOf("more"), events)
        assertEquals(listOf(MenuSound.NAVIGATE), sounds)
    }

    @Test
    fun withoutWriteAccessItExplainsAndOffersToAllow() {
        show(
            SearchUiState(
                query = "x",
                canWrite = false,
                results = listOf(ResultItem(chart("a", "A"), ItemState.Available, null)),
            ),
        )

        composeRule.onNodeWithText("Permitir").performClick()

        assertEquals(listOf("allow"), events)
        composeRule.onNodeWithText("Baixar").assertDoesNotExist()
    }

    @Test
    fun errorsAndFailedDownloadsAreExplained() {
        show(SearchUiState(query = "x", error = SearchError.UNAVAILABLE))
        composeRule.onNodeWithText("Não deu para falar com o Encore.").assertExists()
        composeRule.onNodeWithText("Tentar de novo").performClick()
        assertEquals(listOf("search"), events)
    }

    @Test
    fun aFailedDownloadSaysWhyAndCanBeRetried() {
        show(
            SearchUiState(
                query = "x",
                results = listOf(
                    ResultItem(chart("a", "A"), ItemState.Failed(ChartDownloadException.Reason.BROKEN_FILE), null),
                ),
            ),
        )

        composeRule.onNodeWithText("O arquivo baixado veio com defeito.").assertExists()
        composeRule.onNodeWithText("Tentar de novo").performClick()
        assertEquals(listOf("download:a"), events)
    }

    @Test
    fun theFiltersSoundLikeNavigatingAndReportTheChoice() {
        show(SearchUiState(query = "x"))

        composeRule.onNodeWithText("Versões").performClick()
        composeRule.onNodeWithText("Só com guitarra separada").performClick()

        assertEquals(listOf("versions:true", "guitar:true"), events)
        assertEquals(listOf(MenuSound.NAVIGATE, MenuSound.NAVIGATE), sounds)
    }

    @Test
    fun theGuitarSearchShowsItsProgress() {
        show(
            SearchUiState(query = "x", onlySeparateGuitar = true, guitarSearch = GuitarSearch(37, 100, running = true)),
        )

        composeRule.onNodeWithText("Procurando mapas com guitarra separada… 37 de 100").assertExists()
    }

    @Test
    fun nothingFoundWithTheGuitarFilterOffersToSearchMore() {
        show(
            SearchUiState(
                query = "x",
                onlySeparateGuitar = true,
                guitarSearch = GuitarSearch(100, 100, running = false),
            ),
        )

        composeRule.onNodeWithText("Nenhum mapa com guitarra separada nos primeiros 100 resultados.").assertExists()
        composeRule.onNodeWithText("Procurar mais").performClick()
        assertEquals(listOf("more-guitar"), events)
    }
}
