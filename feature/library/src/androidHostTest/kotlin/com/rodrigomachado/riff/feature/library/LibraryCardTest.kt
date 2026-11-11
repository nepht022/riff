package com.rodrigomachado.riff.feature.library

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** O lado direito do cartão (opção A): a etiqueta da dificuldade mais alta e "GUITARRA" ou "IA". */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class LibraryCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: LibraryUiState) = composeRule.setContent {
        RiffTheme {
            LibraryContent(
                state = state,
                onPickFolder = {},
                onRescan = {},
                onReportDismissed = {},
                onPlay = { _, _ -> },
                onBack = {},
            )
        }
    }

    private fun library(vararg charts: Chart, ai: Set<String> = emptySet()) =
        LibraryUiState(isLoading = false, hasFolder = true, charts = charts.toList(), aiSeparated = ai)

    /** A etiqueta mostra só a dificuldade mais alta, em maiúsculas; o leitor de tela lê "Até Médio". */
    @Test
    fun highestDifficultyTagShowsTheTopOne() {
        show(library(chart("a", difficulties = setOf(Difficulty.MEDIUM, Difficulty.EASY))))

        composeRule.onNodeWithText("MÉDIO").assertExists()
        composeRule.onNodeWithContentDescription("Até Médio").assertExists()
        composeRule.onNodeWithText("FÁCIL").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en")
    fun inEnglishTheTagReadsInEnglish() {
        show(library(chart("a", difficulties = setOf(Difficulty.EASY, Difficulty.EXPERT))))

        composeRule.onNodeWithText("EXPERT").assertExists()
        composeRule.onNodeWithContentDescription("Up to Expert").assertExists()
    }

    @Test
    fun ownGuitarShowsTheGuitarTag() {
        show(library(chart("a").copy(separateGuitar = true)))

        composeRule.onNodeWithText("GUITARRA").assertExists()
        composeRule.onNodeWithContentDescription("Guitarra do mapa").assertExists()
        composeRule.onNodeWithContentDescription("Guitarra separada pela IA").assertDoesNotExist()
    }

    @Test
    fun aiSeparatedShowsTheAiSeal() {
        show(library(chart("a"), ai = setOf("a")))

        composeRule.onNodeWithContentDescription("Guitarra separada pela IA").assertExists()
        composeRule.onNodeWithText("GUITARRA").assertDoesNotExist()
    }

    /** Com as duas coisas, só "GUITARRA": o mapa já tem a guitarra. */
    @Test
    fun ownGuitarWinsOverAi() {
        show(library(chart("a").copy(separateGuitar = true), ai = setOf("a")))

        composeRule.onNodeWithText("GUITARRA").assertExists()
        composeRule.onNodeWithContentDescription("Guitarra separada pela IA").assertDoesNotExist()
    }
}
