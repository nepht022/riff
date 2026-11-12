package com.rodrigomachado.riff.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.component.KeyHintTestTags
import com.rodrigomachado.riff.core.ui.input.Gamepad
import com.rodrigomachado.riff.core.ui.input.LocalGamepad
import com.rodrigomachado.riff.core.ui.input.PadEvent
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/** Um controle do PC que liga e desliga quando o teste quer. */
private class SwitchablePad(on: Boolean) : Gamepad {
    override val events = MutableSharedFlow<PadEvent>(extraBufferCapacity = 16)
    override val connected = MutableStateFlow(on)
    override val presses = MutableSharedFlow<String>(extraBufferCapacity = 16)
}

/**
 * As Configurações do PC (Decisão 10 do design novo): na janela larga, as abas numa coluna à esquerda (Voltar e o
 * título no topo, "Licenças e privacidade" no pé) e as opções à direita; na estreita, as abas no topo, como no
 * celular. A barra de teclas no rodapé, com os símbolos do controle só com um ligado.
 */
class DesktopWideSettingsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val system = Locale.getDefault()
    private var licenses = 0

    @After
    fun restore() = Locale.setDefault(system)

    private fun show(width: Dp, pad: Gamepad = SwitchablePad(on = false)) {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))
        composeRule.setContent {
            CompositionLocalProvider(LocalGamepad provides pad) {
                Box(Modifier.size(width, 768.dp)) {
                    RiffTheme {
                        SettingsContent(
                            settings = Settings.DEFAULT,
                            onMasterVolume = {},
                            onMissVolume = {},
                            onNoteSpeed = {},
                            onLeftHanded = {},
                            onMenuSounds = {},
                            onPreviewMiss = {},
                            onCalibrate = {},
                            onBack = {},
                            onLicenses = { licenses++ },
                            tabs = SettingsTab.entries,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun wideSettingsHasTabsColumn() {
        show(1024.dp)

        val inColumn = hasAnyAncestor(hasTestTag(SettingsTestTags.TABS_COLUMN))
        for (tab in listOf("Geral", "Sons", "Jogo", "Controles", "Visual da pista", "Guitarra IA")) {
            composeRule.onNode(hasText(tab) and inColumn).assertExists()
        }
        composeRule.onNode(hasText("Voltar") and inColumn).assertExists()
        composeRule.onNode(hasText("Configurações") and inColumn).assertExists()
        composeRule.onNode(hasText("Geral") and inColumn).assertIsSelected()
        // As licenças só no pé da coluna (não repetidas na aba Geral).
        composeRule.onNode(hasText("Licenças e privacidade") and inColumn).performClick()
        composeRule.onAllNodes(hasText("Licenças e privacidade")).fetchSemanticsNodes().let {
            assertTrue("licenças repetidas: ${it.size}", it.size == 1)
        }
        assertTrue("o pé da coluna não abriu as licenças", licenses == 1)
        // As opções à direita, fora da coluna; outra aba troca as opções.
        val inOptions = hasAnyAncestor(hasTestTag(SettingsTestTags.OPTIONS))
        composeRule.onNode(hasText("Idioma") and inOptions).assertExists()
        composeRule.onNode(hasText("Sons") and inColumn).performClick()
        composeRule.onNode(hasTestTag(SettingsTestTags.MASTER_VOLUME) and inOptions).assertExists()
        composeRule.onNode(hasText("Sons") and inColumn).assertIsSelected()
        composeRule.onNode(hasText("Controles") and inColumn).performClick()
        composeRule.onNode(hasText("Teclado") and inOptions).assertExists()
        // A barra de teclas da tela.
        for (label in listOf("Abas e opções", "Abas, opções e valores", "Escolher", "Voltar")) {
            composeRule.onNode(hasText(label) and inBar).assertExists()
        }
    }

    @Test
    fun narrowSettingsKeepsTheTabsOnTop() {
        show(600.dp)

        composeRule.onNode(hasTestTag(SettingsTestTags.TABS_COLUMN)).assertDoesNotExist()
        composeRule.onNode(hasText("Geral")).assertIsSelected()
        composeRule.onNode(hasText("Licenças e privacidade")).assertExists()
    }

    @Test
    fun padSymbolsFollowTheConnection() {
        val pad = SwitchablePad(on = false)
        show(1024.dp, pad)

        composeRule.onNode(hasText("Esc") and inBar).assertExists()
        composeRule.onNode(hasText("○") and inBar).assertDoesNotExist()
        composeRule.onNode(hasText("✕") and inBar).assertDoesNotExist()

        pad.connected.value = true
        composeRule.waitForIdle()

        composeRule.onNode(hasText("○") and inBar).assertExists()
        composeRule.onNode(hasText("✕") and inBar).assertExists()
    }

    private companion object {
        val inBar: SemanticsMatcher = hasAnyAncestor(hasTestTag(KeyHintTestTags.BAR))
    }
}
