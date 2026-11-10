package com.rodrigomachado.riff.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.room.RoomDevice
import com.rodrigomachado.riff.core.room.SongStatus
import com.rodrigomachado.riff.core.room.testing.FakeFirebase
import com.rodrigomachado.riff.core.ui.component.DifficultyDialogTestTags
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.PLAY_AGAIN
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.STAGE
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.TIMEOUT_MS
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.TITLE
import com.rodrigomachado.riff.feature.gameplay.GameplayTestTags
import com.rodrigomachado.riff.feature.home.HomeTestTags
import com.rodrigomachado.riff.feature.library.LibraryTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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
    private val firebase = FakeFirebase(clock = System::currentTimeMillis, uid = ME)
    private val guest = firebase.repository(GUEST, RoomDevice.PHONE)

    @After
    fun close() = match.close()

    @Test
    fun ownerPicksSongReadiesAndStartsWithAGuest() {
        val code = openRoomWithAGuest()

        pickTheSongByClick()
        composeRule.onNodeWithText(TITLE).assertExists()
        assertEquals(chartId(), firebase.get("salas/$code/musica/chartId"))
        composeRule.clickOnWindowThread(composeRule.onNode(READY_BUTTON))
        guestGetsReady(code)
        startByClick()

        // O convidado carrega quando a rodada abre; o dono (o PC) marca o começo e a partida começa nele.
        guestLoads(code)
        val start = agreedStart(code)
        match.playInTheMatchUntil("a contagem") { match.exists(hasTestTag(GameplayTestTags.COUNTDOWN)) }
        assertEquals("tocou antes do começo", 0L, match.songPositionMs())
        match.playInTheMatchUntil("a música tocando") { match.songPositionMs() > 0 }
        val startedAt = System.currentTimeMillis() - match.songPositionMs()
        assertTrue("começou em $startedAt, combinado $start", abs(startedAt - start) <= START_TOLERANCE_MS)
        assertEquals(1, firebase.get("salas/$code/jogadores/$ME/carregou"))
    }

    @Test
    fun focusLossDoesNotPauseTheRoomMatch() {
        val code = openRoomWithAGuest(songMs = LONG_SONG_MS)
        playingRoomMatch(code)

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
        val code = openRoomWithAGuest(songMs = LONG_SONG_MS)
        playingRoomMatch(code)

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
        val code = openRoomWithAGuest()

        pressUntilFocused(hasText(CHOOSE_SONG))
        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasTestTag(LibraryTestTags.LIST), TIMEOUT_MS)
        pressUntilFocused(hasText(TITLE))
        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasTestTag(DifficultyDialogTestTags.OPTION), TIMEOUT_MS)
        // A opção do diálogo (o "EXPERT" da linha da Biblioteca, atrás, é outro texto).
        pressUntilFocused(hasText(EXPERT), into = DIFFICULTY_DIALOG)
        press(Key.Enter, into = DIFFICULTY_DIALOG)
        composeRule.waitUntilAtLeastOneExists(hasText(LEAVE_ROOM), TIMEOUT_MS)
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodes(isRoot()).fetchSemanticsNodes().size == 1 }
        composeRule.waitUntilAtLeastOneExists(READY_BUTTON, TIMEOUT_MS)

        pressUntilFocused(hasText(EXPERT) and hasClickAction())
        press(Key.Enter)
        composeRule.waitUntil(TIMEOUT_MS) { firebase.get("salas/$code/jogadores/$ME/dificuldade") == "EXPERT" }
        pressUntilFocused(READY_BUTTON)
        press(Key.Enter)
        composeRule.waitUntil(TIMEOUT_MS) { firebase.get("salas/$code/jogadores/$ME/pronto") == true }
        guestGetsReady(code)
        composeRule.waitUntilAtLeastOneExists(hasText(START) and isEnabled(), TIMEOUT_MS)
        pressUntilFocused(hasText(START))
        composeRule.mainClock.autoAdvance = false
        match.pressKeys { pressKey(Key.Enter) }

        match.playInTheMatchUntil("a partida de sala") { match.exists(STAGE) }
        composeRule.waitUntil(TIMEOUT_MS) { firebase.get("salas/$code/rodada") == 1 }
    }

    /** O Resultado da partida de sala não tem "Jogar de novo" (abriria uma partida sozinho por cima da sala). */
    @Test
    fun roomResultHasNoPlayAgain() {
        val code = openRoomWithAGuest()
        playingRoomMatch(code)

        match.playUntil(hasText(BACK_TO_ROOM))
        composeRule.onNodeWithText(PLAY_AGAIN).assertDoesNotExist()
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(BACK_TO_ROOM))

        composeRule.waitUntilAtLeastOneExists(hasText(LEAVE_ROOM), TIMEOUT_MS)
        composeRule.waitUntil(TIMEOUT_MS) { firebase.get("salas/$code/estado") == "sala" }
        composeRule.onNodeWithTag(GameplayTestTags.STAGE).assertDoesNotExist()
    }

    /** O app com a Sala no Firebase falso, a sala criada pelo PC (o dono) e o convidado (celular) dentro dela. */
    private fun openRoomWithAGuest(songMs: Int = DesktopMatch.SONG_MS): String {
        match.openApp(ranking = FakeRankingScript(), room = firebase, songMs = songMs)
        runBlocking { match.graph.ranking.register(NAME) }
        composeRule.waitForIdle()
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(HomeTestTags.ROOM))
        composeRule.waitUntilAtLeastOneExists(hasText(CREATE), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(CREATE))
        composeRule.waitUntil(TIMEOUT_MS) { (firebase.get("salas") as? JSONObject)?.length() == 1 }
        val code = (firebase.get("salas") as JSONObject).keys().next()
        composeRule.waitUntilAtLeastOneExists(hasText(code), TIMEOUT_MS)
        runBlocking { guest.join(code, GUEST_NAME) }
        composeRule.waitUntilAtLeastOneExists(hasText(GUEST_NAME), TIMEOUT_MS)
        return code
    }

    /** Escolher música → a música na Biblioteca (modo de escolha) → a única dificuldade → de volta à sala. */
    private fun pickTheSongByClick() {
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(CHOOSE_SONG))
        composeRule.waitUntilAtLeastOneExists(hasText(TITLE), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(TITLE))
        composeRule.waitUntilAtLeastOneExists(hasTestTag(DifficultyDialogTestTags.OPTION), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(DifficultyDialogTestTags.OPTION))
        composeRule.waitUntilAtLeastOneExists(hasText(LEAVE_ROOM), TIMEOUT_MS)
        composeRule.waitUntilAtLeastOneExists(READY_BUTTON, TIMEOUT_MS)
    }

    /** O convidado tem a música (o mesmo `chartId`), escolhe Expert e fica pronto. */
    private fun guestGetsReady(code: String) {
        composeRule.waitUntil(TIMEOUT_MS) { firebase.get("salas/$code/musica") != null }
        runBlocking { guest.setMe(code, Difficulty.EXPERT, ready = true, song = SongStatus.HAS) }
    }

    /** Começar (com os dois prontos) abre a partida de sala. */
    private fun startByClick() {
        composeRule.waitUntilAtLeastOneExists(hasText(START) and isEnabled(), TIMEOUT_MS)
        composeRule.mainClock.autoAdvance = false
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(START))
        match.playInTheMatchUntil("a partida de sala") { match.exists(STAGE) }
    }

    /** O convidado carrega a música da rodada 1 assim que ela abre. */
    private fun guestLoads(code: String) {
        match.playInTheMatchUntil("a rodada 1") { firebase.get("salas/$code/rodada") == 1 }
        runBlocking { guest.markLoaded(code, 1) }
    }

    /** O começo marcado pelo dono (hora do servidor). */
    private fun agreedStart(code: String): Long {
        match.playInTheMatchUntil("o começo marcado") { firebase.get("salas/$code/inicio") != null }
        return (firebase.get("salas/$code/inicio") as Number).toLong()
    }

    /** Da sala (com o convidado) à partida de sala já tocando. */
    private fun playingRoomMatch(code: String) {
        pickTheSongByClick()
        composeRule.clickOnWindowThread(composeRule.onNode(READY_BUTTON))
        guestGetsReady(code)
        startByClick()
        guestLoads(code)
        agreedStart(code)
        match.playInTheMatchUntil("a música tocando") { match.songPositionMs() > 0 }
    }

    private fun chartId(): String = runBlocking { match.graph.charts.observeCharts().first().single().id }

    private fun press(key: Key, into: SemanticsMatcher = APP) {
        composeRule.runOnUiThread { composeRule.onNode(into).performKeyInput { pressKey(key) } }
        composeRule.waitForIdle()
    }

    /** ↓ até [matcher] ter o foco; se passar do fim, ↑. */
    private fun pressUntilFocused(matcher: SemanticsMatcher, into: SemanticsMatcher = APP) {
        for (key in listOf(Key.DirectionDown, Key.DirectionUp)) {
            repeat(MAX_PRESSES) {
                if (focused(matcher)) return
                press(key, into)
            }
        }
        assertTrue("${matcher.description} não ficou com o foco (está em ${focusedNow()})", focused(matcher))
    }

    /** O que tem o foco agora (a falha diz onde a seta parou). */
    private fun focusedNow(): List<String> =
        composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().map { it.config.toString() }

    private fun focused(matcher: SemanticsMatcher) = match.exists(matcher and isFocused())

    private companion object {
        const val ME = "u1"
        const val GUEST = "u2"
        const val NAME = "Rodrigo"
        const val GUEST_NAME = "Bia"
        const val CREATE = "Criar sala"
        const val LEAVE_ROOM = "Sair da sala"
        const val CHOOSE_SONG = "Escolher música"
        const val READY = "Pronto"
        const val START = "Começar"
        const val EXPERT = "Expert"
        const val BACK_TO_ROOM = "Voltar à sala"
        const val MAX_PRESSES = 16

        // A música mais longa para os testes que andam dentro dela (a de 2 s acabaria no meio).
        const val LONG_SONG_MS = 8_000
        const val WALK_MS = 300L

        // O começo é por quadro, com a suíte ocupando o PC: uma folga de alguns quadros.
        const val START_TOLERANCE_MS = 250L

        /** O botão Pronto (o carimbo "Pronto" do cartão não é clicável). */
        val READY_BUTTON = hasText(READY) and hasClickAction()
        val LEAVE_PROMPT = hasTestTag(GameplayTestTags.LEAVE_PROMPT)
        val DIFFICULTY_DIALOG = isRoot() and hasAnyDescendant(hasTestTag(DifficultyDialogTestTags.OPTION))
        val APP = isRoot() and !hasAnyDescendant(hasTestTag(DifficultyDialogTestTags.OPTION))
    }
}
