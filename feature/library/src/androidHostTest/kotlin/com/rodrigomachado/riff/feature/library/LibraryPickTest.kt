package com.rodrigomachado.riff.feature.library

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
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
     * pergunta da separação, que é para jogar agora); o título é "Escolher música" e não há "+ Mapas".
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
        composeRule.onNodeWithText("+ Mapas").assertDoesNotExist()
        composeRule.onNodeWithText("Música").performClick()
        composeRule.onNodeWithText("Difícil").performClick()

        assertEquals("a" to Difficulty.HARD, played)
        assertEquals(0, asked)
    }

    /**
     * Na escolha da sala não se exclui nada: sem X nos cartões e sem "Selecionar" no ⋮ (apagar a música que a sala
     * já escolheu deixaria a sala dizendo que todos têm a música, e o Começar abriria um mapa que não existe mais).
     */
    @Test
    fun thePickerHasNoXAndNoSelect() {
        composeRule.setContent {
            RiffTheme {
                LibraryContent(
                    state = LibraryUiState(
                        isLoading = false,
                        hasFolder = true,
                        charts = listOf(chart("a", title = "Música"), chart("b", title = "Outra")),
                    ),
                    onPickFolder = {},
                    onRescan = {},
                    onReportDismissed = {},
                    onPlay = { _, _ -> },
                    onBack = {},
                    pickForRoom = true,
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Excluir", substring = true).assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Mais opções").performClick()
        composeRule.onNodeWithText("Trocar pasta").assertExists()
        composeRule.onNodeWithText("Selecionar").assertDoesNotExist()
    }
}
