package com.rodrigomachado.riff.feature.library

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** O lado direito do cartão (opção B): os quadradinhos das dificuldades e a guitarra do mapa ou "IA". */
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

    /** Os quadradinhos preenchidos são as dificuldades do mapa; o leitor de tela lê uma frase só, na ordem. */
    @Test
    fun difficultySquaresShowWhatTheChartHas() {
        val levels = setOf(Difficulty.HARD, Difficulty.EASY, Difficulty.MEDIUM)
        show(library(chart("a", difficulties = levels), chart("b", difficulties = setOf(Difficulty.EXPERT))))

        composeRule.onNodeWithContentDescription("Dificuldades: Fácil, Médio e Difícil").assertExists()
        composeRule.onNodeWithContentDescription("Dificuldades: Expert").assertExists()
    }

    @Test
    @Config(qualifiers = "en")
    fun inEnglishTheDifficultiesReadInEnglish() {
        show(library(chart("a", difficulties = setOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD))))

        composeRule.onNodeWithContentDescription("Difficulties: Easy, Medium and Hard").assertExists()
    }

    @Test
    fun ownGuitarShowsTheGuitarIcon() {
        show(library(chart("a").copy(separateGuitar = true)))

        composeRule.onNodeWithContentDescription("Guitarra do mapa").assertExists()
        composeRule.onNodeWithContentDescription("Guitarra separada pela IA").assertDoesNotExist()
    }

    @Test
    fun aiSeparatedShowsTheAiSeal() {
        show(library(chart("a"), ai = setOf("a")))

        composeRule.onNodeWithContentDescription("Guitarra separada pela IA").assertExists()
        composeRule.onNodeWithContentDescription("Guitarra do mapa").assertDoesNotExist()
    }

    /** Com as duas coisas, só o ícone do mapa: ele já tem a guitarra. */
    @Test
    fun ownGuitarWinsOverAi() {
        show(library(chart("a").copy(separateGuitar = true), ai = setOf("a")))

        composeRule.onNodeWithContentDescription("Guitarra do mapa").assertExists()
        composeRule.onNodeWithContentDescription("Guitarra separada pela IA").assertDoesNotExist()
    }
}
