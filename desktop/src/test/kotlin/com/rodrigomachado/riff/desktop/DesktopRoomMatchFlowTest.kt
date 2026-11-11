package com.rodrigomachado.riff.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.pressKey
import com.rodrigomachado.riff.core.ui.component.DifficultyDialogTestTags
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.PLAY_AGAIN
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.STAGE
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.TIMEOUT_MS
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.TITLE
import com.rodrigomachado.riff.desktop.DesktopRoom.Companion.BACK_TO_ROOM
import com.rodrigomachado.riff.desktop.DesktopRoom.Companion.CHOOSE_SONG
import com.rodrigomachado.riff.desktop.DesktopRoom.Companion.DIFFICULTY_DIALOG
import com.rodrigomachado.riff.desktop.DesktopRoom.Companion.EXPERT
import com.rodrigomachado.riff.desktop.DesktopRoom.Companion.LEAVE_ROOM
import com.rodrigomachado.riff.desktop.DesktopRoom.Companion.MAX_PRESSES
import com.rodrigomachado.riff.desktop.DesktopRoom.Companion.ME
import com.rodrigomachado.riff.desktop.DesktopRoom.Companion.NOT_READY
import com.rodrigomachado.riff.desktop.DesktopRoom.Companion.READY_BUTTON
import com.rodrigomachado.riff.desktop.DesktopRoom.Companion.START
import com.rodrigomachado.riff.feature.gameplay.GameplayTestTags
import com.rodrigomachado.riff.feature.library.LibraryTestTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.abs

/**
 * A partida de sala no app do PC (o dono), com um convidado no celular pelo Firebase falso (nunca o de verdade): o
 * dono escolhe a música pela Biblioteca, fica pronto com o convidado e começa; a partida espera o começo combinado e
 * começa nele; perder o foco não pausa; Esc pergunta se é para sair (com a música seguindo) e Sair marca o `saiu`; tudo
 * só com o teclado; e o Resultado da partida de sala só volta à sala.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopRoomMatchFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val match = DesktopMatch(composeRule, temp)
    private val room = DesktopRoom(composeRule, match)
    private val firebase = room.firebase

    @After
    fun close() = match.close()

    @Test
    fun ownerPicksSongReadiesAndStartsWithAGuest() {
        val code = room.openRoomWithAGuest()

        room.pickTheSongByClick()
        composeRule.onNodeWithText(TITLE).assertExists()
        assertEquals(room.chartId(), firebase.get("salas/$code/musica/chartId"))
        composeRule.clickOnWindowThread(composeRule.onNode(READY_BUTTON))
        room.guestGetsReady(code)
        room.startByClick()

        // O convidado carrega quando a rodada abre; o dono (o PC) marca o começo e a partida começa nele.
        room.guestLoads(code)
        val start = room.agreedStart(code)
        match.playInTheMatchUntil("a contagem") { match.exists(hasTestTag(GameplayTestTags.COUNTDOWN)) }
        assertEquals("tocou antes do começo", 0L, match.songPositionMs())
        match.playInTheMatchUntil("a música tocando") { match.songPositionMs() > 0 }
        val startedAt = System.currentTimeMillis() - match.songPositionMs()
        assertTrue("começou em $startedAt, combinado $start", abs(startedAt - start) <= START_TOLERANCE_MS)
        assertEquals(1, firebase.get("salas/$code/jogadores/$ME/carregou"))
    }

    @Test
    fun focusLossDoesNotPauseTheRoomMatch() {
        val code = room.openRoomWithAGuest(songMs = LONG_SONG_MS)
        room.playingRoomMatch(code)

        match.windowFocused = false
        val before = match.songPositionMs()
        match.playInTheMatchUntil("a música andando sem o foco") { match.songPositionMs() >= before + WALK_MS }

        assertFalse("pausou", match.exists(hasTestTag(GameplayTestTags.PAUSE_MENU)))
        assertFalse("perguntou se é para sair", match.exists(hasTestTag(GameplayTestTags.LEAVE_PROMPT)))
        match.windowFocused = true
    }

    /** Esc pergunta (a música segue); Esc de novo é Continuar tocando; Sair (setas e Enter) marca o `saiu`. */
    @Test
    fun escInTheRoomMatchAsksToLeaveAndMarksLeft() {
        val code = room.openRoomWithAGuest(songMs = LONG_SONG_MS)
        room.playingRoomMatch(code)

        match.pressKeys { pressKey(Key.Escape) }
        match.playInTheMatchUntil("a pergunta de sair") { match.exists(LEAVE_PROMPT) }
        val asked = match.songPositionMs()
        match.playInTheMatchUntil("a música andando com a pergunta") { match.songPositionMs() >= asked + WALK_MS }
        assertFalse("pausou", match.exists(hasTestTag(GameplayTestTags.PAUSE_MENU)))

        match.pressKeys { pressKey(Key.Escape) }
        match.playInTheMatchUntil("a pergunta fechada") { !match.exists(LEAVE_PROMPT) }
        assertEquals(null, firebase.get("salas/$code/jogadores/$ME/saiu"))

        match.pressKeys { pressKey(Key.Escape) }
        match.playInTheMatchUntil("a pergunta de novo") { match.exists(LEAVE_PROMPT) }
        repeat(MAX_PRESSES) {
            if (!match.exists(hasTestTag(GameplayTestTags.EXIT) and isFocused())) {
                match.pressKeys { pressKey(Key.DirectionDown) }
                match.playInTheMatchUntil("a seta") { true }
            }
        }
        assertTrue("o Sair não ficou com o foco", match.exists(hasTestTag(GameplayTestTags.EXIT) and isFocused()))
        match.pressKeys { pressKey(Key.Enter) }

        match.playUntil(hasText(LEAVE_ROOM))
        composeRule.waitUntil(TIMEOUT_MS) { firebase.get("salas/$code/jogadores/$ME/saiu") == 1 }
        composeRule.waitUntil(TIMEOUT_MS) { firebase.get("salas/$code/estado") == "sala" }
    }

    /** Só o teclado, da sala à partida: Escolher música, a música e a dificuldade, Pronto e Começar. */
    @Test
    fun onlyKeyboardPicksDifficultyReadyAndStart() {
        val code = room.openRoomWithAGuest()

        room.pressUntilFocused(hasText(CHOOSE_SONG))
        room.press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasTestTag(LibraryTestTags.LIST), TIMEOUT_MS)
        room.pressUntilFocused(hasText(TITLE))
        room.press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasTestTag(DifficultyDialogTestTags.OPTION), TIMEOUT_MS)
        // A opção do diálogo (o "EXPERT" da linha da Biblioteca, atrás, é outro texto).
        room.pressUntilFocused(hasText(EXPERT), into = DIFFICULTY_DIALOG)
        room.press(Key.Enter, into = DIFFICULTY_DIALOG)
        composeRule.waitUntilAtLeastOneExists(hasText(LEAVE_ROOM), TIMEOUT_MS)
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodes(isRoot()).fetchSemanticsNodes().size == 1 }
        composeRule.waitUntilAtLeastOneExists(READY_BUTTON, TIMEOUT_MS)

        room.pressUntilFocused(hasText(EXPERT) and hasClickAction())
        room.press(Key.Enter)
        composeRule.waitUntil(TIMEOUT_MS) { firebase.get("salas/$code/jogadores/$ME/dificuldade") == "EXPERT" }
        room.pressUntilFocused(READY_BUTTON)
        room.press(Key.Enter)
        composeRule.waitUntil(TIMEOUT_MS) { firebase.get("salas/$code/jogadores/$ME/pronto") == true }
        room.guestGetsReady(code)
        composeRule.waitUntilAtLeastOneExists(hasText(START) and isEnabled(), TIMEOUT_MS)
        // O foco continua no "Não estou pronto"; o Começar fica ao lado, à direita.
        composeRule.onNode(hasText(NOT_READY) and hasClickAction()).assertIsFocused()
        room.press(Key.DirectionRight)
        room.pressUntilFocused(hasText(START))
        composeRule.mainClock.autoAdvance = false
        match.pressKeys { pressKey(Key.Enter) }

        match.playInTheMatchUntil("a partida de sala") { match.exists(STAGE) }
        composeRule.waitUntil(TIMEOUT_MS) { firebase.get("salas/$code/rodada") == 1 }
    }

    /** O Resultado da partida de sala não tem "Jogar de novo" (abriria uma partida sozinho por cima da sala). */
    @Test
    fun roomResultHasNoPlayAgain() {
        val code = room.openRoomWithAGuest()
        room.playingRoomMatch(code)

        match.playUntil(hasText(BACK_TO_ROOM))
        composeRule.onNodeWithText(PLAY_AGAIN).assertDoesNotExist()
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(BACK_TO_ROOM))

        composeRule.waitUntilAtLeastOneExists(hasText(LEAVE_ROOM), TIMEOUT_MS)
        composeRule.waitUntil(TIMEOUT_MS) { firebase.get("salas/$code/estado") == "sala" }
        composeRule.onNodeWithTag(GameplayTestTags.STAGE).assertDoesNotExist()
    }

    private companion object {
        // A música mais longa para os testes que andam dentro dela (a de 2 s acabaria no meio).
        const val LONG_SONG_MS = 8_000
        const val WALK_MS = 300L

        // O começo é por quadro, com a suíte ocupando o PC: uma folga de alguns quadros.
        const val START_TOLERANCE_MS = 250L

        val LEAVE_PROMPT = hasTestTag(GameplayTestTags.LEAVE_PROMPT)
    }
}
