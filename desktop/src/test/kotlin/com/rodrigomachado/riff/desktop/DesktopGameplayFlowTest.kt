package com.rodrigomachado.riff.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.pressKey
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.BACK
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.PLAY_AGAIN
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.STAGE
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.TIMEOUT_MS
import com.rodrigomachado.riff.feature.gameplay.GameplayTestTags
import com.rodrigomachado.riff.feature.library.LibraryTestTags
import com.rodrigomachado.riff.feature.results.ResultsTestTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A partida no app do PC, de ponta a ponta: Biblioteca → dificuldade → partida (tocando de verdade, com o FFmpeg
 * decodificando o tom da pasta) → Resultado com o recorde gravado; "Jogar de novo" e Sair não empilham telas.
 * O som sai numa saída falsa que anda no ritmo do relógio (o CI não tem placa de som), [DesktopMatch].
 */
@OptIn(ExperimentalTestApi::class)
class DesktopGameplayFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val match = DesktopMatch(composeRule, temp)

    @After
    fun close() = match.close()

    @Test
    fun theLibraryPlaysASongToTheResultsAndBackWithoutStackingScreens() {
        match.openApp()
        match.openLibrary()

        // Biblioteca → a música → a única dificuldade → a partida.
        match.startTheSong()
        // A cor verde segurada antes e a palheta ↓ na hora da primeira nota (a segunda passa: erro).
        match.playTheFirstNote(Key.A)
        match.playUntil(hasText(PLAY_AGAIN))

        val record = match.record()
        assertNotNull("o recorde foi gravado", record)
        assertEquals("as duas notas foram julgadas", 2, record!!.perfect + record.good + record.miss)
        assertTrue("a palheta acertou a primeira nota: $record", record.perfect + record.good >= 1)

        // Jogar de novo: a partida no lugar do Resultado.
        composeRule.mainClock.autoAdvance = false
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(PLAY_AGAIN))
        match.playInTheMatchUntil("a partida") { match.exists(STAGE) }
        composeRule.onNodeWithText(PLAY_AGAIN).assertDoesNotExist()
        // Esc pausa e Sair volta à Biblioteca (não ao Resultado de antes).
        match.pressKeys { pressKey(Key.Escape) }
        match.playInTheMatchUntil("a pausa") { match.exists(hasTestTag(GameplayTestTags.EXIT)) }
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(GameplayTestTags.EXIT))
        match.playUntil(hasTestTag(LibraryTestTags.LIST))
        composeRule.onNodeWithText(PLAY_AGAIN).assertDoesNotExist()

        // Outra partida até o fim; do Resultado, Voltar leva à Biblioteca.
        match.startTheSong()
        match.playUntil(hasText(PLAY_AGAIN))
        // O botão (a barra de teclas do PC também diz "Voltar", do Esc).
        composeRule.clickOnWindowThread(composeRule.onNode(BACK_BUTTON))
        composeRule.waitUntilAtLeastOneExists(hasTestTag(LibraryTestTags.LIST), TIMEOUT_MS)
        composeRule.onNodeWithText(PLAY_AGAIN).assertDoesNotExist()
        composeRule.onNodeWithTag(GameplayTestTags.STAGE).assertDoesNotExist()
    }

    /**
     * O Resultado só pelo teclado (o controle manda as mesmas teclas): ↓ chega à linha dos botões, → e ← andam entre
     * "Voltar" e "Jogar de novo" (lado a lado), Enter no Voltar volta à Biblioteca. A barra de teclas fica no rodapé.
     */
    @Test
    fun pcKeyboardReachesTheResultButtons() {
        match.openApp()
        match.openLibrary()
        match.startTheSong()
        match.playUntil(hasText(PLAY_AGAIN))
        composeRule.onNodeWithTag(ResultsTestTags.KEY_HINTS).assertExists()

        pressUntilFocused(Key.DirectionDown, hasText(PLAY_AGAIN) or BACK_BUTTON)
        pressUntilFocused(Key.DirectionRight, hasText(PLAY_AGAIN))
        pressUntilFocused(Key.DirectionLeft, BACK_BUTTON)
        press(Key.Enter)

        composeRule.waitUntilAtLeastOneExists(hasTestTag(LibraryTestTags.LIST), TIMEOUT_MS)
        composeRule.onNodeWithText(PLAY_AGAIN).assertDoesNotExist()
    }

    private fun press(key: Key) {
        match.pressKeys { pressKey(key) }
        composeRule.waitForIdle()
    }

    private fun pressUntilFocused(key: Key, matcher: SemanticsMatcher) {
        repeat(MAX_PRESSES) {
            if (match.exists(matcher and isFocused())) return
            press(key)
        }
        assertTrue("${matcher.description} não ficou com o foco", match.exists(matcher and isFocused()))
    }

    private companion object {
        /** O "Voltar" que é botão (não o rótulo do Esc na barra de teclas). */
        val BACK_BUTTON = hasText(BACK) and hasClickAction()
        const val MAX_PRESSES = 10
    }
}
