package com.rodrigomachado.riff.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import com.rodrigomachado.riff.core.room.testing.FakeFirebase
import com.rodrigomachado.riff.core.room.testing.FakePlayer
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.TIMEOUT_MS
import com.rodrigomachado.riff.feature.home.HomeTestTags
import com.rodrigomachado.riff.feature.room.RoomTestTags
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A Sala no app do PC, com o Firebase falso (nunca o de verdade): "em breve" sem configuração; criar mostra o código
 * e o próprio jogador (no PC); entrar digitando o código pelo teclado; outro jogador aparece ao vivo; Esc sai da
 * sala; e todos os botões das telas da Sala alcançados só com o teclado.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopRoomFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val match = DesktopMatch(composeRule, temp)
    private val script = FakeRankingScript()
    private val firebase = FakeFirebase(clock = System::currentTimeMillis, uid = ME)
    private val clipboard = FakeClipboard()

    @After
    fun close() = match.close()

    @Test
    fun roomTileWithoutConfigIsComingSoon() {
        match.openApp(ranking = script)
        composeRule.waitForIdle()

        val room = composeRule.onNodeWithTag(HomeTestTags.ROOM)
        room.assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Sala, em breve").assertExists()
        composeRule.clickOnWindowThread(room)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(CREATE).assertDoesNotExist()
        assertTrue("falou com o Firebase: ${firebase.writes}", firebase.writes.isEmpty())
    }

    @Test
    fun createShowsTheCodeAndMe() {
        openWithRoom()
        composeRule.onNodeWithTag(HomeTestTags.ROOM).assertIsEnabled()

        val code = createByClick()

        composeRule.onNodeWithText(NAME).assertExists()
        composeRule.onNodeWithText(YOU).assertExists()
        composeRule.onNodeWithText(OWNER).assertExists()
        composeRule.onNodeWithText(PC).assertExists()
        assertEquals("pc", firebase.get("salas/$code/jogadores/$ME/aparelho"))
        assertEquals(NAME, firebase.get("salas/$code/jogadores/$ME/nome"))
    }

    /** Só o teclado: as setas até a caixa do código, as letras (minúsculas), o Backspace apaga e o Enter entra. */
    @Test
    fun joinByKeyboardTypesTheCodeAndEnters() {
        firebase.seedRoom(SEEDED, owner = OTHER, players = listOf(FakePlayer(OTHER, OTHER_NAME)))
        openWithRoom()

        openRoomByKeyboard()
        composeRule.waitUntilAtLeastOneExists(hasText(CREATE), TIMEOUT_MS)
        pressUntilFocused(hasText(JOIN_WITH_CODE))
        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasTestTag(RoomTestTags.CODE), TIMEOUT_MS)
        pressUntilFocused(hasTestTag(RoomTestTags.CODE))

        "bcdx".forEach { type(it) }
        press(Key.Backspace)
        type('f')
        composeRule.waitUntilAtLeastOneExists(hasText("F"), TIMEOUT_MS)
        composeRule.onNodeWithText("X").assertDoesNotExist()
        press(Key.Enter)

        composeRule.waitUntilAtLeastOneExists(hasText(SEEDED), TIMEOUT_MS)
        composeRule.waitUntilAtLeastOneExists(hasText(NAME), TIMEOUT_MS)
        composeRule.onNodeWithText(OTHER_NAME).assertExists()
        assertEquals(NAME, firebase.get("salas/$SEEDED/jogadores/$ME/nome"))
    }

    @Test
    fun anotherPlayerJoiningAppearsLive() {
        openWithRoom()
        val code = createByClick()
        composeRule.onNodeWithText(OTHER_NAME).assertDoesNotExist()

        runBlocking { firebase.repository(OTHER).join(code, OTHER_NAME) }

        composeRule.waitUntilAtLeastOneExists(hasText(OTHER_NAME), TIMEOUT_MS)
        composeRule.onNodeWithText(PHONE).assertExists()
    }

    /** Esc na sala sai dela (o último apaga a sala) e volta à escolha; Esc de novo, à tela inicial. */
    @Test
    fun escLeavesTheRoom() {
        openWithRoom()
        val code = createByClick()

        press(Key.Escape)

        composeRule.waitUntilAtLeastOneExists(hasText(CREATE), TIMEOUT_MS)
        composeRule.waitUntil(TIMEOUT_MS) { firebase.get("salas/$code") == null }
        assertNull(firebase.get("salas/$code"))
        press(Key.Escape)
        composeRule.waitUntilAtLeastOneExists(hasTestTag(HomeTestTags.SOLO), TIMEOUT_MS)
    }

    /** Fechar a janela com a sala aberta sai dela (aqui, o último: a sala é apagada), em vez de virar fantasma. */
    @Test
    fun closingTheAppLeavesTheRoom() {
        openWithRoom()
        val code = createByClick()

        match.close()

        assertNull(firebase.get("salas/$code"))
    }

    /** Com o Firebase sem responder, fechar não espera a saída da sala mais que ~2 s. */
    @Test
    fun closingTheAppDoesNotWaitLongForAStalledRoom() {
        openWithRoom()
        val code = createByClick()
        firebase.stalled = true

        val start = System.nanoTime()
        match.close()
        val closingMs = (System.nanoTime() - start) / NANOS_PER_MS

        assertTrue("fechar levou $closingMs ms", closingMs < CLOSE_LIMIT_MS)
        assertEquals(ME, firebase.get("salas/$code/dono")) // não saiu: some para os outros em 60 s
    }

    /** Cada botão da escolha, da caixa do código e da sala, só com as setas, Enter e Esc. */
    @Test
    fun onlyKeyboardReachesEveryButton() {
        firebase.seedRoom(SEEDED, owner = OTHER, players = listOf(FakePlayer(OTHER, OTHER_NAME)))
        openWithRoom()

        openRoomByKeyboard()
        composeRule.waitUntilAtLeastOneExists(hasText(CREATE), TIMEOUT_MS)
        listOf(hasText(BACK), hasText(CREATE), hasText(JOIN_WITH_CODE)).forEach { pressUntilFocused(it) }

        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasTestTag(RoomTestTags.CODE), TIMEOUT_MS)
        pressUntilFocused(hasText(BACK))
        pressUntilFocused(hasTestTag(RoomTestTags.CODE))
        SEEDED.forEach { type(it) }
        pressUntilFocused(hasText(JOIN))
        press(Key.Enter)

        composeRule.waitUntilAtLeastOneExists(hasText(OTHER_NAME), TIMEOUT_MS)
        pressUntilFocused(hasText(LEAVE))
        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasText(CREATE), TIMEOUT_MS)
        composeRule.waitUntil(TIMEOUT_MS) { firebase.get("salas/$SEEDED/jogadores/$ME") == null }

        pressUntilFocused(hasText(CREATE))
        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasText(LEAVE), TIMEOUT_MS)
        composeRule.waitUntilAtLeastOneExists(hasText(NAME), TIMEOUT_MS)
        press(Key.Escape)
        composeRule.waitUntilAtLeastOneExists(hasText(CREATE), TIMEOUT_MS)
        press(Key.Escape)
        composeRule.waitUntilAtLeastOneExists(hasTestTag(HomeTestTags.SOLO), TIMEOUT_MS)
    }

    /**
     * Só o teclado: as setas chegam ao botão do código (no PC, "Copiar o código"; no topo, à direita do "Sair da
     * sala": ↑ até ele e →) e o Enter copia o código para a área de transferência e diz "Código copiado".
     */
    @Test
    fun pcShareCopiesTheCode() {
        openWithRoom()
        val code = createByClick()
        composeRule.onNodeWithText(COPIED).assertDoesNotExist()

        pressUntilFocused(hasText(LEAVE))
        press(Key.DirectionRight)
        assertTrue("→ não chegou ao copiar (está em ${focusedNow()})", focused(hasContentDescription(COPY)))
        press(Key.Enter)

        composeRule.waitUntilAtLeastOneExists(hasText(COPIED), TIMEOUT_MS)
        assertEquals(code, clipboard.text)
        composeRule.onNode(hasTestTag(RoomTestTags.KEY_HINTS)).assertExists() // a barra de teclas do PC
    }

    /** O app com a Sala ligada no Firebase falso e o nome do ranking já escolhido. */
    private fun openWithRoom() {
        match.openApp(ranking = script, room = firebase, clipboard = clipboard)
        runBlocking { match.graph.ranking.register(NAME) }
        composeRule.waitForIdle()
    }

    /** Na tela inicial, ↓ até o Sozinho, → até a Sala (ao lado dele) e Enter. */
    private fun openRoomByKeyboard() {
        pressUntilFocused(hasTestTag(HomeTestTags.SOLO))
        press(Key.DirectionRight)
        assertTrue("→ não chegou à Sala", focused(hasTestTag(HomeTestTags.ROOM)))
        press(Key.Enter)
    }

    /** Tela inicial → Sala → Criar sala; devolve o código da sala criada, já na tela. */
    private fun createByClick(): String {
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(HomeTestTags.ROOM))
        composeRule.waitUntilAtLeastOneExists(hasText(CREATE), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(CREATE))
        composeRule.waitUntil(TIMEOUT_MS) { (firebase.get("salas") as? JSONObject)?.length() == 1 }
        val code = (firebase.get("salas") as JSONObject).keys().next()
        composeRule.waitUntilAtLeastOneExists(hasText(code), TIMEOUT_MS)
        composeRule.waitUntilAtLeastOneExists(hasText(NAME), TIMEOUT_MS)
        return code
    }

    /**
     * Uma letra na caixa do código (com o foco). A tecla injetada pelo teste não gera o caractere digitado (o KEY_TYPED
     * do AWT, que o app de verdade recebe): a letra entra como texto, a mesma entrada do teclado na caixa.
     */
    private fun type(letter: Char) {
        composeRule.runOnUiThread {
            composeRule.onNode(hasTestTag(RoomTestTags.CODE) and isFocused()).performTextInput(letter.toString())
        }
        composeRule.waitForIdle()
    }

    private fun press(key: Key) {
        composeRule.runOnUiThread { composeRule.onRoot().performKeyInput { pressKey(key) } }
        composeRule.waitForIdle()
    }

    /** ↓ até [matcher] ter o foco; se passar do fim, ↑. */
    private fun pressUntilFocused(matcher: SemanticsMatcher) {
        for (key in listOf(Key.DirectionDown, Key.DirectionUp)) {
            repeat(MAX_PRESSES) {
                if (focused(matcher)) return
                press(key)
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
        const val OTHER = "u2"
        const val NAME = "Rodrigo"
        const val OTHER_NAME = "Bia"
        const val SEEDED = "BCDF"
        const val CREATE = "Criar sala"
        const val JOIN_WITH_CODE = "Entrar com código"
        const val JOIN = "Entrar"
        const val BACK = "Voltar"
        const val LEAVE = "Sair da sala"
        const val YOU = "você"
        const val COPY = "Copiar o código"
        const val COPIED = "Código copiado"
        const val OWNER = "Dono"
        const val PC = "PC"
        const val PHONE = "Celular"
        const val MAX_PRESSES = 8
        const val NANOS_PER_MS = 1_000_000L

        // os ~2 s da espera e uma folga para fechar o banco e o resto com a suíte ocupando o PC
        const val CLOSE_LIMIT_MS = 4_000L
    }
}
