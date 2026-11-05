package com.rodrigomachado.riff.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import com.rodrigomachado.riff.core.audio.MenuSoundPlayer
import com.rodrigomachado.riff.core.audio.SfxPlayerFactory
import com.rodrigomachado.riff.desktop.pad.NoPadSource
import com.rodrigomachado.riff.feature.home.HomeTestTags
import com.rodrigomachado.riff.feature.report.JsonPost
import com.rodrigomachado.riff.feature.report.ReportConfig
import com.rodrigomachado.riff.feature.report.ReportTestTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/**
 * O Reportar no app do PC, pelo envelope da tela inicial, com o mesmo formulário e o mesmo script do celular. O envio
 * é falso (os testes nunca mandam relato de verdade): anota o JSON e a thread.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopReportFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private var graph: DesktopGraph? = null

    @After
    fun close() {
        graph?.close()
        DesktopLocale.applyDefault()
    }

    @Test
    fun theEnvelopeOpensTheReportAndSendsIt() {
        val script = FakeScript()
        openReport(script)

        chooseCategory("Jogo lento ou engasgando")
        write("A música engasga no refrão.")
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(ReportTestTags.SEND))

        composeRule.waitUntilAtLeastOneExists(hasText(SENT), TIMEOUT_MS)
        val (url, json) = script.posts.single()
        assertEquals(URL, url)
        assertTrue(json, json.contains(""""categoria":"Jogo lento ou engasgando""""))
        assertTrue(json, json.contains(""""texto":"A música engasga no refrão.""""))
        assertTrue(json, json.contains(""""sistema":"""))
        assertFalse(json, json.contains("modelo"))
    }

    @Test
    fun withoutAnAddressTheReportSaysItIsNotConfigured() {
        openReport(FakeScript(), url = "")

        composeRule.waitUntilAtLeastOneExists(hasText(NOT_CONFIGURED), TIMEOUT_MS)
    }

    @Test
    fun withoutTheInternetTheTextStays() {
        openReport(FakeScript(failure = IOException("sem rede")))

        chooseCategory("Jogo lento ou engasgando")
        write("Travou.")
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(ReportTestTags.SEND))

        composeRule.waitUntilAtLeastOneExists(hasText(FAILED, substring = true), TIMEOUT_MS)
        assertTrue(exists(hasTestTag(ReportTestTags.TEXT) and hasText("Travou.")))
        // A janela segue respondendo: Voltar leva à tela inicial.
        composeRule.clickOnWindowThread(composeRule.onNodeWithText("Voltar"))
        composeRule.waitUntilAtLeastOneExists(hasTestTag(HomeTestTags.SOLO), TIMEOUT_MS)
    }

    @Test
    fun theDeviceLineShowsWhatGoes() {
        val script = FakeScript()
        openReport(script)
        val line = "Riff ${ReportConfig.APP_VERSION} · ${System.getProperty("os.name")} "

        composeRule.waitUntilAtLeastOneExists(hasText(line, substring = true), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(DEVICE))
        chooseCategory("Jogo lento ou engasgando")
        write("Sem as informações.")
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(ReportTestTags.SEND))

        composeRule.waitUntilAtLeastOneExists(hasText(SENT), TIMEOUT_MS)
        val json = script.posts.single().second
        assertFalse(json, json.contains("aparelho"))
        assertFalse(json, json.contains("sistema"))
    }

    @Test
    fun keyboardOnlyReport() {
        val script = FakeScript()
        openApp(script)
        pressUntilFocused(Key.DirectionDown, hasContentDescription(ENVELOPE))
        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasText(CATEGORY_HINT), TIMEOUT_MS)

        // ↓ do tipo à categoria; Enter abre a lista, e o Esc fecha só a lista.
        pressUntilFocused(Key.DirectionDown, hasText("Problema"))
        press(Key.DirectionDown)
        assertTrue("↓ não foi do tipo à categoria", focused(hasText(CATEGORY_HINT)))
        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasText(CLOSE), TIMEOUT_MS)
        press(Key.Escape, into = DIALOG)
        composeRule.waitUntil(TIMEOUT_MS) { !exists(hasText(CLOSE)) }
        composeRule.onNodeWithText(CATEGORY_HINT).assertExists()

        // De novo: ↓ e Enter escolhem.
        pressUntilFocused(Key.DirectionDown, hasText(CATEGORY_HINT))
        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasText(CLOSE), TIMEOUT_MS)
        pressUntilFocused(Key.DirectionDown, hasText("Jogo lento ou engasgando"), into = DIALOG)
        press(Key.Enter, into = DIALOG)
        composeRule.waitUntil(TIMEOUT_MS) { !exists(hasText(CLOSE)) }
        composeRule.onNodeWithText("Jogo lento ou engasgando").assertExists()

        // O texto: ↑/↓ andam nele (o foco fica), e o Tab sai sem escrever a tabulação.
        val text = hasTestTag(ReportTestTags.TEXT)
        pressUntilFocused(Key.DirectionDown, text)
        write("Linha 1\nLinha 2")
        press(Key.DirectionUp)
        press(Key.DirectionDown)
        assertTrue("↑/↓ tiraram o foco do texto", focused(text))
        press(Key.Tab)
        assertFalse("o Tab não saiu do texto", focused(text))
        assertTrue("o Tab não foi ao email", focused(hasTestTag(ReportTestTags.CONTACT)))
        pressShiftTab()
        assertTrue("o Shift+Tab não voltou ao texto", focused(text))
        press(Key.Tab)

        // O email (uma linha) sai com ↓; Enter no Enviar.
        pressUntilFocused(Key.DirectionDown, hasText("Enviar"))
        press(Key.Enter)

        composeRule.waitUntilAtLeastOneExists(hasText(SENT), TIMEOUT_MS)
        val json = script.posts.single().second
        assertTrue(json, json.contains(""""texto":"Linha 1\nLinha 2""""))
    }

    /** O app na tela inicial; o Reportar fala com o script por [script] em [url]. */
    private fun openApp(script: JsonPost, url: String = URL): DesktopGraph {
        val graph = DesktopGraph(
            temp.newFolder("dados"),
            menuSounds = MenuSoundPlayer { _, _ -> },
            sfx = SfxPlayerFactory { SilentSfx },
            padSource = NoPadSource,
            reportUrl = url,
            reportTransport = script,
            // Sem o ranking (desligado, como sem ranking.url): não é o que se testa aqui, e nunca o script real.
            rankingUrl = "",
            rankingTransport = OfflineRankingScript,
        ).also { graph = it }
        DesktopLocale.applyDefault()
        composeRule.setContent {
            val real = LocalWindowInfo.current
            val focused = remember(real) { FocusedWindow(real) }
            CompositionLocalProvider(LocalWindowInfo provides focused) { RiffDesktopApp(graph) }
        }
        composeRule.waitUntilAtLeastOneExists(hasTestTag(HomeTestTags.SOLO), TIMEOUT_MS)
        composeRule.waitForIdle()
        return graph
    }

    /** O app e, pelo envelope da tela inicial, o Reportar. */
    private fun openReport(script: JsonPost, url: String = URL) {
        openApp(script, url)
        composeRule.clickOnWindowThread(composeRule.onNodeWithContentDescription(ENVELOPE))
        composeRule.waitUntilAtLeastOneExists(hasText(CATEGORY_HINT), TIMEOUT_MS)
    }

    private fun chooseCategory(name: String) {
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(CATEGORY_HINT))
        composeRule.waitUntilAtLeastOneExists(hasText(name), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(name))
        composeRule.waitUntil(TIMEOUT_MS) { !exists(hasText(CLOSE)) }
    }

    private fun write(text: String) {
        composeRule.runOnUiThread { composeRule.onNodeWithTag(ReportTestTags.TEXT).performTextInput(text) }
        composeRule.waitForIdle()
    }

    /** Na janela [into] (o app; a lista de categorias aberta é outra janela, com a própria raiz). */
    private fun press(key: Key, into: SemanticsMatcher = APP) {
        composeRule.runOnUiThread { composeRule.onNode(into).performKeyInput { pressKey(key) } }
        composeRule.waitForIdle()
    }

    private fun pressShiftTab() {
        composeRule.runOnUiThread {
            composeRule.onNode(APP).performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
        }
        composeRule.waitForIdle()
    }

    private fun pressUntilFocused(key: Key, matcher: SemanticsMatcher, into: SemanticsMatcher = APP) {
        repeat(MAX_PRESSES) {
            if (focused(matcher)) return
            press(key, into)
        }
        assertTrue("${matcher.description} não ficou com o foco", focused(matcher))
    }

    private fun exists(matcher: SemanticsMatcher) = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun focused(matcher: SemanticsMatcher) = exists(matcher and isFocused())

    /** O script falso: anota cada envio (endereço e JSON) e responde como o de verdade; com [failure], sem rede. */
    private class FakeScript(private val failure: IOException? = null) : JsonPost {
        val posts = mutableListOf<Pair<String, String>>()

        override suspend fun post(url: String, json: String): Pair<Int, String> {
            failure?.let { throw it }
            synchronized(posts) { posts += url to json }
            return 200 to """{"ok":true}"""
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val MAX_PRESSES = 12
        const val URL = "https://script.google.com/macros/s/teste/exec"
        const val ENVELOPE = "Reportar problema"
        const val CATEGORY_HINT = "Escolha uma categoria"
        const val CLOSE = "Fechar"
        const val DEVICE = "Incluir informações do aparelho"
        const val SENT = "Obrigado! Recebemos seu relato."
        const val FAILED = "Não deu para enviar"
        const val NOT_CONFIGURED = "O envio de relatos não está configurado nesta versão do Riff."

        val DIALOG = isRoot() and hasAnyDescendant(hasText(CLOSE))
        val APP = isRoot() and !hasAnyDescendant(hasText(CLOSE))
    }
}
