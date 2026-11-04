package com.rodrigomachado.riff.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import com.rodrigomachado.riff.core.audio.MenuSoundPlayer
import com.rodrigomachado.riff.core.audio.SfxPlayerFactory
import com.rodrigomachado.riff.desktop.pad.NoPadSource
import com.rodrigomachado.riff.feature.home.HomeTestTags
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * "Licenças e privacidade" no app do PC, pelas Configurações: a privacidade do PC (sem ranking, que é da etapa 4d) e
 * as licenças do que vai no app do PC (com a guitarra por IA, da etapa 4c), abertas pelo teclado.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopLicensesFlowTest {
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
    fun theSettingsOpenTheLicenses() {
        openLicenses()

        composeRule.onNodeWithText("Seus mapas, recordes e configurações ficam no PC", substring = true).assertExists()
        assertTrue("a privacidade do PC é a do celular", !exists(hasText("backup do Android", substring = true)))
        // O ranking chega ao PC na 4d: o texto diz o que vai para a planilha e o que o vínculo faz.
        composeRule.onNodeWithText("um código aleatório de cada aparelho", substring = true).assertExists()
        composeRule.onNodeWithText("juntar vários aparelhos sob o mesmo nome", substring = true).assertExists()
        val names = listOf(AI_MODEL, "demucs.onnx", "ONNX Runtime", "JTransforms", "LWJGL", "GLFW", "Oswald", "FFmpeg")
        for (name in names) {
            scrollTo(name)
            composeRule.onNodeWithText(name).assertExists()
        }
        // As bibliotecas do Gradle, do aboutlibraries.json gerado no build do app do PC; o ONNX Runtime fica só na
        // lista manual (com o aviso de copyright de verdade).
        scrollTo("Compose Runtime")
        assertTrue("o ONNX Runtime do Gradle repetido", runCatching { scrollTo("onnxruntime") }.isFailure)
    }

    @Test
    fun enterOpensALicenseText() {
        openLicenses()

        pressUntilFocused(Key.DirectionDown, hasText(AI_MODEL))
        press(Key.Enter)

        composeRule.waitUntilAtLeastOneExists(
            hasText("Copyright (c) Meta Platforms, Inc. and affiliates.", substring = true),
            TIMEOUT_MS,
        )
        press(Key.Escape)
        composeRule.waitUntilAtLeastOneExists(hasText("Geral"), TIMEOUT_MS)
    }

    /** O app na tela inicial → Configurações → Licenças e privacidade. */
    private fun openLicenses() {
        val graph = DesktopGraph(
            temp.newFolder("dados"),
            menuSounds = MenuSoundPlayer { _, _ -> },
            sfx = SfxPlayerFactory { SilentSfx },
            padSource = NoPadSource,
        ).also { graph = it }
        DesktopLocale.applyDefault()
        composeRule.setContent {
            val real = LocalWindowInfo.current
            val focused = remember(real) { FocusedWindow(real) }
            CompositionLocalProvider(LocalWindowInfo provides focused) { RiffDesktopApp(graph) }
        }
        composeRule.waitUntilAtLeastOneExists(hasTestTag(HomeTestTags.SOLO), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithContentDescription("Configurações"))
        composeRule.waitUntilAtLeastOneExists(hasText("Licenças e privacidade"), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithText("Licenças e privacidade"))
        composeRule.waitUntilAtLeastOneExists(hasText("Privacidade"), TIMEOUT_MS)
        // A lista chega depois (lida fora da thread da janela); o modelo da IA é o primeiro da lista manual.
        composeRule.waitUntil(TIMEOUT_MS) { runCatching { scrollTo(AI_MODEL) }.isSuccess }
        composeRule.waitForIdle()
    }

    private fun scrollTo(text: String) =
        composeRule.runOnUiThread { composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text)) }

    private fun press(key: Key) {
        composeRule.runOnUiThread { composeRule.onRoot().performKeyInput { pressKey(key) } }
        composeRule.waitForIdle()
    }

    private fun pressUntilFocused(key: Key, matcher: SemanticsMatcher) {
        repeat(MAX_PRESSES) {
            if (focused(matcher)) return
            press(key)
        }
        assertTrue("${matcher.description} não ficou com o foco", focused(matcher))
    }

    private fun exists(matcher: SemanticsMatcher) = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun focused(matcher: SemanticsMatcher) = exists(matcher and isFocused())

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val MAX_PRESSES = 20
        const val AI_MODEL = "Demucs v4 (htdemucs_6s)"
    }
}
