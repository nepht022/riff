package com.rodrigomachado.riff.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.room.Room
import com.rodrigomachado.riff.core.room.RoomDevice
import com.rodrigomachado.riff.core.room.RoomPlayer
import com.rodrigomachado.riff.core.room.RoomSong
import com.rodrigomachado.riff.core.room.SongStatus
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.feature.room.MySong
import com.rodrigomachado.riff.feature.room.RoomLobbyActions
import com.rodrigomachado.riff.feature.room.RoomLobbyContent
import com.rodrigomachado.riff.feature.room.RoomLobbyUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** O Pronto da sala no teclado do PC: Enter troca o botão para "Não estou pronto" sem perder o foco. */
class DesktopRoomReadyFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun resetLocale() = DesktopLocale.applyDefault()

    @Test
    fun enterOnReadyKeepsTheFocusAndEnterAgainUndoes() {
        DesktopLocale.applyDefault()
        var ready by mutableStateOf(false)
        var toggles = 0
        composeRule.setContent {
            RiffTheme {
                RoomLobbyContent(
                    RoomLobbyUiState.Ready(room(ready), mySong = MySong.Has),
                    "KQZX",
                    onLeave = {},
                    actions = RoomLobbyActions(
                        onToggleReady = {
                            toggles++
                            ready = !ready
                        },
                    ),
                )
            }
        }
        composeRule.waitForIdle()

        pressUntilFocused(Key.DirectionDown, button(READY))
        press(Key.Enter)
        assertEquals(1, toggles)
        assertTrue("o foco saiu do botão", focused(button(NOT_READY)))

        press(Key.Enter)
        assertEquals(2, toggles)
        assertTrue("o foco saiu do botão", focused(button(READY)))
    }

    /** Eu (Bia, convidada) com a música e a dificuldade; a Ana é a dona. */
    private fun room(ready: Boolean) = Room(
        code = "KQZX",
        owner = "Ana",
        players = listOf(
            RoomPlayer("Ana", "Ana", RoomDevice.PHONE, 0, 0, gone = false, song = SongStatus.HAS),
            RoomPlayer(
                "Bia", "Bia", RoomDevice.PC, 1, 1, gone = false,
                difficulty = Difficulty.EXPERT, ready = ready, song = SongStatus.HAS,
            ),
        ),
        me = "Bia",
        song = RoomSong("mapa", "Through the Fire", "DragonForce", setOf(Difficulty.EXPERT), null, null),
    )

    private fun button(text: String) = hasText(text) and hasClickAction()

    private fun press(key: Key) {
        composeRule.runOnUiThread { composeRule.onNode(isRoot()).performKeyInput { pressKey(key) } }
        composeRule.waitForIdle()
    }

    private fun pressUntilFocused(key: Key, matcher: SemanticsMatcher) {
        repeat(MAX_PRESSES) {
            if (focused(matcher)) return
            press(key)
        }
        assertTrue("${matcher.description} não ficou com o foco", focused(matcher))
    }

    private fun focused(matcher: SemanticsMatcher) =
        composeRule.onAllNodes(matcher and isFocused()).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val READY = "Pronto"
        const val NOT_READY = "Não estou pronto"
        const val MAX_PRESSES = 12
    }
}
