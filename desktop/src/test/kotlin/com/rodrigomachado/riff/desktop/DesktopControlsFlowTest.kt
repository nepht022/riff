package com.rodrigomachado.riff.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.rodrigomachado.riff.core.model.ControlAction
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.PLAY_AGAIN
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.TIMEOUT_MS
import com.rodrigomachado.riff.feature.home.HomeTestTags
import com.rodrigomachado.riff.feature.settings.ControlsTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Os controles escolhidos nas Configurações do PC valem na partida seguinte: a tecla escolhida para o Verde toca o
 * Verde, e sem palheta apertar a cor na hora basta. Partida de verdade, como em [DesktopGameplayFlowTest].
 */
@OptIn(ExperimentalTestApi::class)
class DesktopControlsFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val match = DesktopMatch(composeRule, temp)

    @After
    fun close() = match.close()

    @Test
    fun aKeyChosenInTheSettingsPlaysTheMatch() {
        match.openApp()
        openTheControls()
        // A primeira linha "Verde" é a do teclado; a outra, a do controle.
        composeRule.clickOnWindowThread(composeRule.onAllNodesWithText("Verde").onFirst())
        composeRule.waitUntilAtLeastOneExists(hasText("Aperte a tecla para Verde"), TIMEOUT_MS)
        composeRule.runOnUiThread {
            composeRule.onNodeWithTag(ControlsTestTags.CHOOSING).performKeyInput { pressKey(Key.Q) }
        }
        waitForSettings { it.controls.keys[ControlAction.FRET_1] == Key.Q.keyCode }

        playTheFirstNote(Key.Q, strum = Key.DirectionDown)
    }

    @Test
    fun withoutStrumTheMatchJudgesThePress() {
        match.openApp()
        openTheControls()
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(ControlsTestTags.STRUM))
        waitForSettings { !it.controls.strum }

        playTheFirstNote(Key.A, strum = null)
    }

    /** Da tela inicial, pelo ícone do canto, às Configurações e à aba Controles. */
    private fun openTheControls() {
        composeRule.clickOnWindowThread(composeRule.onNodeWithContentDescription("Configurações"))
        composeRule.waitUntilAtLeastOneExists(hasText("Controles"), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithText("Controles"))
        composeRule.waitUntilAtLeastOneExists(hasText("Tocar com palheta"), TIMEOUT_MS)
    }

    private fun waitForSettings(done: (Settings) -> Boolean) =
        composeRule.waitUntil(TIMEOUT_MS) { runBlocking { done(match.graph.settings.settings.first()) } }

    /** Volta à tela inicial, abre a partida e toca a primeira nota assim; ela tem de virar acerto. */
    private fun playTheFirstNote(fret: Key, strum: Key?) {
        composeRule.clickOnWindowThread(composeRule.onNodeWithText("Voltar"))
        composeRule.waitUntilAtLeastOneExists(hasTestTag(HomeTestTags.SOLO), TIMEOUT_MS)
        match.openLibrary()
        match.startTheSong()
        match.playTheFirstNote(fret, strum)
        match.playUntil(hasText(PLAY_AGAIN))

        val record = match.record()
        assertNotNull("o recorde foi gravado", record)
        assertTrue("a primeira nota foi acerto: $record", record!!.perfect + record.good >= 1)
    }
}
