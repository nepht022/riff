package com.rodrigomachado.riff.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import com.rodrigomachado.riff.core.audio.MenuSoundPlayer
import com.rodrigomachado.riff.core.audio.SfxPlayerFactory
import com.rodrigomachado.riff.desktop.pad.NoPadSource
import com.rodrigomachado.riff.feature.calibration.CalibrationTestTags
import com.rodrigomachado.riff.feature.calibration.ClickTrackPlayer
import com.rodrigomachado.riff.feature.calibration.calibrationClockMs
import com.rodrigomachado.riff.feature.home.HomeTestTags
import com.rodrigomachado.riff.feature.settings.SettingsTestTags
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.abs

/**
 * As Configurações no app do PC: abertas pela tela inicial, com a aba da IA (etapa 4c) e as Licenças; o que se
 * muda fica gravado; a calibração pelo teclado salva o atraso e volta às Configurações; trocar o idioma refaz a tela
 * sem perder a pilha.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopSettingsFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    /** O metrônomo de mentira: devolve os cliques que o teste montar a partir das teclas apertadas. */
    private val clicks = CompletableDeferred<List<Long>>()
    private var graph: DesktopGraph? = null

    @After
    fun close() {
        graph?.close()
        DesktopLocale.applyDefault()
    }

    @Test
    fun homeOpensTheSettingsWithTheAiTab() {
        openSettings()

        for (tab in listOf("Geral", "Sons", "Jogo", "Visual da pista", "Guitarra IA")) {
            composeRule.onNodeWithText(tab).assertExists()
        }
        composeRule.onNodeWithText("Licenças e privacidade").assertExists()
    }

    @Test
    fun aChangedSettingIsKept() {
        val graph = openSettings()

        click("Jogo")
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(SettingsTestTags.LEFT_HANDED))

        composeRule.waitUntil(TIMEOUT_MS) { runBlocking { graph.settings.settings.first().leftHanded } }
    }

    @Test
    fun savingTheCalibrationAppliesTheOffset() {
        val graph = openSettings()
        click("Jogo")
        click("Calibrar atraso do áudio")
        click("Começar")
        composeRule.waitUntilAtLeastOneExists(hasTestTag(CalibrationTestTags.TAP_AREA), TIMEOUT_MS)
        composeRule.waitForIdle() // a área pega o foco do teclado

        // Cada tecla cai 40 ms depois do seu clique: o clique é montado a partir do instante da tecla (lido na thread
        // da janela, logo antes de ela ser entregue), em vez de o teste correr atrás de um metrônomo em tempo real.
        val taps = (0 until TAPS).map {
            Thread.sleep(TAP_GAP_MS)
            composeRule.runOnUiThread {
                val before = calibrationClockMs()
                composeRule.onRoot().performKeyInput { keyDown(Key.Spacebar) }
                val after = calibrationClockMs()
                composeRule.onRoot().performKeyInput { keyUp(Key.Spacebar) }
                (before + after) / 2
            }
        }
        clicks.complete(taps.map { it - LATE_MS })
        composeRule.waitUntilAtLeastOneExists(hasText("Salvar"), TIMEOUT_MS)
        click("Salvar")

        composeRule.waitUntilAtLeastOneExists(hasText("Configurações"), TIMEOUT_MS)
        val offset = runBlocking { graph.settings.settings.first().audioOffsetMs }
        assertTrue("atraso salvo: $offset", abs(offset + LATE_MS) <= TOLERANCE_MS)
        composeRule.onNodeWithText("Salvar").assertDoesNotExist()
    }

    @Test
    fun switchingToEnglishKeepsTheSettingsOpen() {
        openSettings()

        click("Idioma")
        click("English")

        composeRule.waitUntilAtLeastOneExists(hasText("Settings"), TIMEOUT_MS)
        click("Back")
        composeRule.waitUntilAtLeastOneExists(hasTestTag(HomeTestTags.SOLO), TIMEOUT_MS)
        composeRule.onNodeWithText("Settings").assertDoesNotExist()
        // A tela inicial também no idioma novo.
        composeRule.onNodeWithText("Solo").assertExists()
    }

    /** O app aberto na tela inicial e, pelo ícone do canto, as Configurações. */
    private fun openSettings(): DesktopGraph {
        val graph = DesktopGraph(
            temp.newFolder("dados"),
            menuSounds = MenuSoundPlayer { _, _ -> },
            sfx = SfxPlayerFactory { SilentSfx },
            clickTrack = ClickTrackPlayer { clicks.await() },
            padSource = NoPadSource,
        ).also { graph = it }
        DesktopLocale.applyDefault()
        composeRule.setContent {
            // A janela do teste não disputa o foco do sistema: sem isso a calibração pararia como num Alt+Tab.
            val real = LocalWindowInfo.current
            val focused = remember(real) { FocusedWindow(real) }
            CompositionLocalProvider(LocalWindowInfo provides focused) { RiffDesktopApp(graph) }
        }
        composeRule.clickOnWindowThread(composeRule.onNodeWithContentDescription("Configurações"))
        composeRule.waitUntilAtLeastOneExists(hasText("Geral"), TIMEOUT_MS)
        return graph
    }

    private fun click(text: String) {
        composeRule.waitUntilAtLeastOneExists(hasText(text), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(text))
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L

        /** 4 de entrada (não contam) e 20 medidos. */
        const val TAPS = 24

        /** Longe o bastante para cada tecla ficar mais perto do próprio clique que do seguinte. */
        const val TAP_GAP_MS = 150L
        const val LATE_MS = 40
        const val TOLERANCE_MS = 5
    }
}
