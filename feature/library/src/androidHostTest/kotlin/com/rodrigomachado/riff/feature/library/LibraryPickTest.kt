package com.rodrigomachado.riff.feature.library

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A Biblioteca no modo de escolha da sala (o dono escolhe a música e a dificuldade, sem jogar). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class LibraryPickTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var played: Pair<String, Difficulty>? = null

    /**
     * No modo de escolha da sala, o toque abre o diálogo de dificuldade de sempre e a escolha volta direto (sem a
     * pergunta da separação, que é para jogar agora); o título é "Escolher música" e não há Pesquisar.
     */
    @Test
    fun ownerPicksASongFromTheLibrary() {
        var asked = 0
        composeRule.setContent {
            RiffTheme {
                LibraryContent(
                    state = LibraryUiState(
                        isLoading = false,
                        hasFolder = true,
                        charts = listOf(chart("a", title = "Música", difficulties = setOf(Difficulty.HARD))),
                    ),
                    onPickFolder = {},
                    onRescan = {},
                    onReportDismissed = {},
                    onPlay = { id, difficulty -> played = id to difficulty },
                    onBack = {},
                    onSearch = {},
                    onDifficultyChosen = { _, _ -> asked++ },
                    pickForRoom = true,
                )
            }
        }

        composeRule.onNodeWithText("Escolher música").assertExists()
        composeRule.onNodeWithText("Pesquisar Mapas").assertDoesNotExist()
        composeRule.onNodeWithText("Música").performClick()
        composeRule.onNodeWithText("Difícil").performClick()

        assertEquals("a" to Difficulty.HARD, played)
        assertEquals(0, asked)
    }
}
