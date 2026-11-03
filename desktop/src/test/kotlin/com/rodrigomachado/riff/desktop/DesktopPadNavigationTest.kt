package com.rodrigomachado.riff.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.rodrigomachado.riff.core.model.ControlAction
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.PLAY_AGAIN
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.STAGE
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.TIMEOUT_MS
import com.rodrigomachado.riff.desktop.pad.MappedButton
import com.rodrigomachado.riff.feature.home.HomeTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.event.KeyEvent

/**
 * Os menus do PC só com o controle (um falso, lido pela thread `riff-pad` de verdade): o direcional anda, ✕ confirma,
 * ○ volta. Com a partida tocando e na escolha de um botão, o controle não aperta tecla nenhuma nos menus; na pausa,
 * Start fica com a partida (continua) e não vira Enter.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopPadNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val match = DesktopMatch(composeRule, temp)
    private val pad = TestPad()
    private val keys = MenuKeyLog(composeRule)

    @After
    fun close() = match.close()

    @Test
    fun aPadAloneOpensTheLibraryAndComesBack() {
        openApp()

        pressUntilFocused(MappedButton.DPAD_DOWN, hasTestTag(HomeTestTags.SOLO))
        pressAndDeliver(MappedButton.A)
        composeRule.waitUntilAtLeastOneExists(hasText("Biblioteca"), TIMEOUT_MS)
        pressAndDeliver(MappedButton.B)

        composeRule.waitUntil(TIMEOUT_MS) { !match.exists(hasText("Biblioteca")) }
        composeRule.waitUntilAtLeastOneExists(hasTestTag(HomeTestTags.SOLO), TIMEOUT_MS)
    }

    /** ✕ com a partida tocando é uma cor, não o Enter (que pausaria); segurado até o Resultado, não aciona nada lá. */
    @Test
    fun whilePlayingThePadDoesNotPressMenuKeys() {
        openApp()
        match.openLibrary()
        match.startTheSong()
        match.playTo(PRESS_AT_MS)

        pad.hold(MappedButton.A)
        playFor(SETTLE_MS)

        assertTrue("o ✕ pausou a partida", !match.exists(hasText(PAUSED)))
        match.playUntil(hasText(PLAY_AGAIN))
        pad.release(MappedButton.A)
        settle()
        composeRule.onNodeWithText(PLAY_AGAIN).assertExists()
        assertTrue("o Resultado saiu", !match.exists(STAGE))
        assertEquals("teclas mandadas aos menus", emptyList<Int>(), keys.sent)
    }

    /** Na pausa, Start continua (como antes) e não vira também um Enter: nem sai, nem pausa de novo. */
    @Test
    fun startInThePauseOnlyResumes() {
        openApp()
        match.openLibrary()
        match.startTheSong()
        match.playTo(PRESS_AT_MS)
        pad.press(MappedButton.START)
        match.playInTheMatchUntil("a pausa") { match.exists(hasText(PAUSED)) }

        pad.press(MappedButton.START)
        match.playInTheMatchUntil("a partida de volta") { !match.exists(hasText(PAUSED)) }
        playFor(SETTLE_MS)

        assertTrue("pausou de novo", !match.exists(hasText(PAUSED)))
        assertTrue("saiu da partida", match.exists(STAGE))
        assertEquals("teclas mandadas aos menus", emptyList<Int>(), keys.sent)
    }

    /** Na aba Controles, o ✕ apertado no diálogo do botão é o escolhido, e não confirma nada (o diálogo não reabre). */
    @Test
    fun choosingAButtonDoesNotAlsoConfirm() {
        openApp()
        composeRule.clickOnWindowThread(composeRule.onNodeWithContentDescription("Configurações"))
        composeRule.waitUntilAtLeastOneExists(hasText("Controles"), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithText("Controles"))
        composeRule.waitUntilAtLeastOneExists(hasText("Tocar com palheta"), TIMEOUT_MS)
        // A primeira linha "Verde" é a do teclado; a outra, a do controle.
        val padGreen = composeRule.onAllNodesWithText("Verde").onLast()
        composeRule.runOnUiThread { padGreen.performScrollTo() }
        composeRule.clickOnWindowThread(padGreen)
        composeRule.waitUntilAtLeastOneExists(hasText(CHOOSING_GREEN), TIMEOUT_MS)

        pad.press(MappedButton.A)

        composeRule.waitUntil(TIMEOUT_MS) {
            runBlocking { match.graph.settings.settings.first().controls.pad[ControlAction.FRET_1] } == "m:0"
        }
        composeRule.waitUntil(TIMEOUT_MS) { !match.exists(hasText(CHOOSING_GREEN)) }
        settle()
        assertTrue("o diálogo reabriu", !match.exists(hasText(CHOOSING_GREEN)))
        assertEquals("teclas mandadas aos menus", emptyList<Int>(), keys.sent)
        // Fechado o diálogo, o controle volta a andar nos menus.
        pressAndDeliver(MappedButton.DPAD_DOWN)
        assertEquals(listOf(KeyEvent.VK_DOWN), keys.sent)
    }

    private fun openApp() {
        match.openApp(pad, keys.send)
        composeRule.waitForIdle()
    }

    /** Aperta [button] e espera a tecla dele chegar à janela. */
    private fun pressAndDeliver(button: Int) {
        val before = keys.sent.size
        pad.press(button)
        composeRule.waitUntil(TIMEOUT_MS) { keys.sent.size > before }
        composeRule.waitForIdle()
    }

    private fun pressUntilFocused(button: Int, matcher: SemanticsMatcher) {
        repeat(MAX_PRESSES) {
            if (match.exists(matcher and isFocused())) return
            pressAndDeliver(button)
        }
        assertTrue("${matcher.description} não ficou com o foco", match.exists(matcher and isFocused()))
    }

    /** A partida anda [ms] (pelo relógio da parede): tempo para uma tecla que não devia chegar. */
    private fun playFor(ms: Long) {
        val until = System.currentTimeMillis() + ms
        match.playInTheMatchUntil("$ms ms de partida") { System.currentTimeMillis() >= until }
    }

    /** Dá tempo a uma tecla que não devia chegar. */
    private fun settle() {
        val until = System.currentTimeMillis() + SETTLE_MS
        while (System.currentTimeMillis() < until) {
            composeRule.waitForIdle()
            Thread.sleep(SETTLE_STEP_MS)
        }
    }

    private companion object {
        const val PAUSED = "Pausado"
        const val CHOOSING_GREEN = "Aperte o botão para Verde"
        const val PRESS_AT_MS = 300L
        const val SETTLE_MS = 300L
        const val SETTLE_STEP_MS = 20L
        const val MAX_PRESSES = 8
    }
}
