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
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.room.RoomDevice
import com.rodrigomachado.riff.core.room.RoomRepository
import com.rodrigomachado.riff.core.room.SongStatus
import com.rodrigomachado.riff.core.room.testing.FakeFirebase
import com.rodrigomachado.riff.core.ui.component.DifficultyDialogTestTags
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.STAGE
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.TIMEOUT_MS
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.TITLE
import com.rodrigomachado.riff.feature.home.HomeTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue

/**
 * A sala no app do PC (o dono, [ME]) com um convidado no celular ([guest]) pelo Firebase falso (nunca o de verdade),
 * para os testes da partida de sala: abrir a sala, escolher a música, ficar pronto, começar e o teclado (as setas até
 * o foco chegar num botão).
 */
@OptIn(ExperimentalTestApi::class)
internal class DesktopRoom(private val composeRule: ComposeContentTestRule, private val match: DesktopMatch) {
    val firebase = FakeFirebase(clock = System::currentTimeMillis, uid = ME)
    val guest: RoomRepository = firebase.repository(GUEST, RoomDevice.PHONE)

    /** O app com a Sala no Firebase falso, a sala criada pelo PC (o dono) e o convidado (celular) dentro dela. */
    fun openRoomWithAGuest(songMs: Int = DesktopMatch.SONG_MS): String {
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
    fun pickTheSongByClick() {
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(CHOOSE_SONG))
        composeRule.waitUntilAtLeastOneExists(hasText(TITLE), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(TITLE))
        composeRule.waitUntilAtLeastOneExists(hasTestTag(DifficultyDialogTestTags.OPTION), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(DifficultyDialogTestTags.OPTION))
        composeRule.waitUntilAtLeastOneExists(hasText(LEAVE_ROOM), TIMEOUT_MS)
        composeRule.waitUntilAtLeastOneExists(READY_BUTTON, TIMEOUT_MS)
    }

    /** O convidado tem a música (o mesmo `chartId`), escolhe Expert e fica pronto. */
    fun guestGetsReady(code: String) {
        composeRule.waitUntil(TIMEOUT_MS) { firebase.get("salas/$code/musica") != null }
        runBlocking { guest.setMe(code, Difficulty.EXPERT, ready = true, song = SongStatus.HAS) }
    }

    /** Começar (com os dois prontos) abre a partida de sala. */
    fun startByClick() {
        composeRule.waitUntilAtLeastOneExists(hasText(START) and isEnabled(), TIMEOUT_MS)
        composeRule.mainClock.autoAdvance = false
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(START))
        match.playInTheMatchUntil("a partida de sala") { match.exists(STAGE) }
    }

    /** O convidado carrega a música da rodada 1 assim que ela abre. */
    fun guestLoads(code: String) {
        match.playInTheMatchUntil("a rodada 1") { firebase.get("salas/$code/rodada") == 1 }
        runBlocking { guest.markLoaded(code, 1) }
    }

    /** O começo marcado pelo dono (hora do servidor). */
    fun agreedStart(code: String): Long {
        match.playInTheMatchUntil("o começo marcado") { firebase.get("salas/$code/inicio") != null }
        return (firebase.get("salas/$code/inicio") as Number).toLong()
    }

    /** Da sala (com o convidado) à partida de sala já tocando. */
    fun playingRoomMatch(code: String) {
        pickTheSongByClick()
        composeRule.clickOnWindowThread(composeRule.onNode(READY_BUTTON))
        guestGetsReady(code)
        startByClick()
        guestLoads(code)
        agreedStart(code)
        match.playInTheMatchUntil("a música tocando") { match.songPositionMs() > 0 }
    }

    fun chartId(): String = runBlocking { match.graph.charts.observeCharts().first().single().id }

    fun press(key: Key, into: SemanticsMatcher = APP) {
        composeRule.runOnUiThread { composeRule.onNode(into).performKeyInput { pressKey(key) } }
        composeRule.waitForIdle()
    }

    /** ↓ até [matcher] ter o foco; se passar do fim, ↑. */
    fun pressUntilFocused(matcher: SemanticsMatcher, into: SemanticsMatcher = APP) {
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

    companion object {
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

        /** O botão Pronto (o carimbo "Pronto" do cartão não é clicável). */
        val READY_BUTTON = hasText(READY) and hasClickAction()
        val DIFFICULTY_DIALOG = isRoot() and hasAnyDescendant(hasTestTag(DifficultyDialogTestTags.OPTION))
        val APP = isRoot() and !hasAnyDescendant(hasTestTag(DifficultyDialogTestTags.OPTION))
    }
}
