package com.rodrigomachado.riff.feature.room

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class RoomScreensTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val events = mutableListOf<String>()

    @Test
    fun entryCreatesOrGoesToTheCode() {
        composeRule.setContent {
            RiffTheme {
                RoomEntryContent(RoomEntryUiState(), onCreate = { events += "create" }, onJoin = { events += "join" })
            }
        }

        composeRule.onNodeWithText("Criar sala").performClick()
        composeRule.onNodeWithText("Entrar com código").performClick()

        assertEquals(listOf("create", "join"), events)
    }

    @Test
    fun entryWithoutInternetSaysSo() {
        composeRule.setContent {
            RiffTheme {
                RoomEntryContent(
                    RoomEntryUiState(error = RoomError.NO_CONNECTION),
                    onCreate = {},
                    onJoin = {},
                )
            }
        }

        composeRule.onNodeWithText("Sem conexão. Tente de novo.").assertExists()
    }

    /** O campo guarda o que o ViewModel normalizou; Entrar só com as 4 letras. */
    @Test
    fun joinTypesTheCodeAndEnters() {
        var state by mutableStateOf(RoomJoinUiState())
        composeRule.setContent {
            RiffTheme {
                RoomJoinContent(
                    state,
                    onCode = { state = state.copy(code = it.uppercase().take(4)) },
                    onJoin = { events += "join:${state.code}" },
                )
            }
        }

        composeRule.onNodeWithText("Código da sala").assertExists()
        composeRule.onNodeWithText("Entrar").assertIsNotEnabled()
        composeRule.onNodeWithTag(RoomTestTags.CODE).performTextInput("kqzx")
        composeRule.onNodeWithText("Entrar").assertIsEnabled().performClick()

        assertEquals(listOf("join:KQZX"), events)
    }

    @Test
    fun joinErrorsAreClear() {
        var state by mutableStateOf(RoomJoinUiState(code = "KQZX", error = RoomError.NOT_FOUND))
        composeRule.setContent { RiffTheme { RoomJoinContent(state, onCode = {}, onJoin = {}) } }

        composeRule.onNodeWithText("Sala não encontrada.").assertExists()
        state = state.copy(error = RoomError.FULL)
        composeRule.onNodeWithText("Sala cheia (4 jogadores).").assertExists()
        state = state.copy(error = RoomError.NO_CONNECTION)
        composeRule.onNodeWithText("Sem conexão. Tente de novo.").assertExists()
    }

    @Test
    fun lobbyShowsTheCodeThePlayersAndLeave() {
        val room = sampleRoom("Ana", "Bia", me = "Bia", gone = setOf("Bia"), pcs = setOf("Bia"))
        composeRule.setContent {
            RiffTheme { RoomLobbyContent(RoomLobbyUiState.Ready(room), "KQZX", onLeave = { events += "leave" }) }
        }

        composeRule.onNodeWithText("KQZX").assertExists()
        composeRule.onNodeWithText("Ana").assertExists()
        composeRule.onNodeWithText("Dono").assertExists()
        composeRule.onNodeWithText("Celular").assertExists()
        composeRule.onNodeWithText("PC").assertExists()
        composeRule.onNodeWithText("sumiu").assertExists()
        composeRule.onNodeWithText("Aguardando jogadores…").assertExists()
        composeRule.onNodeWithText("Sair da sala").performClick()

        assertEquals(listOf("leave"), events)
    }

    @Test
    fun aFullRoomStopsWaiting() {
        val room = sampleRoom("Ana", "Bia", "Caio", "Duda")
        composeRule.setContent { RiffTheme { RoomLobbyContent(RoomLobbyUiState.Ready(room), "KQZX", onLeave = {}) } }

        composeRule.onNodeWithText("Aguardando jogadores…").assertDoesNotExist()
    }

    @Test
    fun anEndedRoomGoesBack() {
        composeRule.setContent {
            RiffTheme { RoomLobbyContent(RoomLobbyUiState.Ended, "KQZX", onLeave = { events += "back" }) }
        }

        composeRule.onNodeWithText("A sala acabou.").assertExists()
        composeRule.onNodeWithText("Voltar").performClick()

        assertEquals(listOf("back"), events)
    }
}
