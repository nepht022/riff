package com.rodrigomachado.riff.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(BACK))
        composeRule.waitUntilAtLeastOneExists(hasTestTag(LibraryTestTags.LIST), TIMEOUT_MS)
        composeRule.onNodeWithText(PLAY_AGAIN).assertDoesNotExist()
        composeRule.onNodeWithTag(GameplayTestTags.STAGE).assertDoesNotExist()
    }
}
