package com.rodrigomachado.riff.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rodrigomachado.riff.core.room.RoundScore
import com.rodrigomachado.riff.core.ui.component.KeyHintTestTags
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.TIMEOUT_MS
import com.rodrigomachado.riff.desktop.DesktopRoom.Companion.BACK_TO_ROOM
import com.rodrigomachado.riff.desktop.DesktopRoom.Companion.CREATE
import com.rodrigomachado.riff.desktop.DesktopRoom.Companion.GUEST_NAME
import com.rodrigomachado.riff.desktop.DesktopRoom.Companion.LEAVE_ROOM
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * O Resultado da sala no app do PC (o dono), com um convidado no celular pelo Firebase falso: depois da partida de
 * sala, "Ver a sala" abre a classificação da rodada, que atualiza quando o placar final do convidado chega atrasado;
 * "Voltar à sala" volta à sala sem deixar telas empilhadas; tudo só com o teclado, com a barra de teclas no rodapé.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopRoomResultsFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val match = DesktopMatch(composeRule, temp)
    private val room = DesktopRoom(composeRule, match)

    @After
    fun close() = match.close()

    @Test
    fun pcKeyboardReachesBackToTheRoom() {
        val code = room.openRoomWithAGuest()
        room.playingRoomMatch(code)

        // O Resultado de sempre (a música acabou): "Ver a sala" pelas setas e Enter. "Voltar à sala" e "Ver a sala"
        // ficam lado a lado: ↓ chega à linha deles, → vai ao "Ver a sala".
        match.playUntil(hasText(SEE_ROOM))
        room.pressUntilFocused(hasText(SEE_ROOM) or hasText(BACK_TO_ROOM))
        if (!match.exists(hasText(SEE_ROOM) and isFocused())) room.press(Key.DirectionRight)
        room.pressUntilFocused(hasText(SEE_ROOM))
        room.press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasText(ROOM_RESULTS), TIMEOUT_MS)
        composeRule.waitUntilAtLeastOneExists(hasText(GUEST_NAME), TIMEOUT_MS)
        // A barra de teclas do PC no rodapé do Resultado da sala.
        composeRule.onNode(hasText("Voltar") and hasAnyAncestor(hasTestTag(KeyHintTestTags.BAR))).assertExists()

        // O final do convidado chega depois (conexão ruim): a classificação atualiza com a tela aberta.
        runBlocking {
            room.guest.publishScore(
                code,
                1,
                RoundScore(GUEST_POINTS, combo = 0, maxCombo = 50, hits = 2, notes = 2, accuracy = 1f, finished = true),
            )
        }
        composeRule.waitUntilAtLeastOneExists(hasText(GUEST_POINTS_TEXT, substring = true), TIMEOUT_MS)

        room.pressUntilFocused(hasText(BACK_TO_ROOM))
        room.press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasText(LEAVE_ROOM), TIMEOUT_MS)
        composeRule.onNodeWithText(ROOM_RESULTS).assertDoesNotExist()
        composeRule.onNodeWithText(SEE_ROOM).assertDoesNotExist()

        // Sem telas empilhadas: sair da sala (Esc) volta direto à escolha da Sala, não a um Resultado.
        room.press(Key.Escape)
        composeRule.waitUntilAtLeastOneExists(hasText(CREATE), TIMEOUT_MS)
        composeRule.onNodeWithText(SEE_ROOM).assertDoesNotExist()
        composeRule.onNodeWithText(ROOM_RESULTS).assertDoesNotExist()
    }

    private companion object {
        const val SEE_ROOM = "Ver a sala"
        const val ROOM_RESULTS = "Resultado da sala"
        const val GUEST_POINTS = 12_345L
        const val GUEST_POINTS_TEXT = "12.345"
    }
}
