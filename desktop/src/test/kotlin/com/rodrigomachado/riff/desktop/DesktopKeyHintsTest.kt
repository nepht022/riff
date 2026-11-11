package com.rodrigomachado.riff.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.rodrigomachado.riff.core.room.testing.FakeFirebase
import com.rodrigomachado.riff.core.ui.component.KeyHintTestTags
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.TIMEOUT_MS
import com.rodrigomachado.riff.desktop.FakeRankingScript.Companion.line
import com.rodrigomachado.riff.feature.home.HomeTestTags
import com.rodrigomachado.riff.feature.library.LibraryTestTags
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A barra de teclas do PC (Decisão 4 do design novo): cada tela fora da partida mostra no rodapé as suas teclas, e
 * os símbolos do controle só aparecem com um controle ligado.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopKeyHintsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val match = DesktopMatch(composeRule, temp)
    private val script = FakeRankingScript()

    @After
    fun close() = match.close()

    /**
     * Passa por todas as telas do PC (menos a partida) e confere a barra com as teclas de cada uma: a tela inicial, as
     * Configurações (e a Calibração e as Licenças abertas de lá), o nome e o vínculo do ranking, o Reportar, o Ranking
     * (lista e placar), a Biblioteca e a pesquisa no Encore, e a escolha da Sala e o entrar com código. Sem controle
     * ligado, nenhuma barra mostra os símbolos dele. (O Resultado e a sala têm os testes deles; o Resultado da sala,
     * o `DesktopRoomResultsFlowTest`.)
     */
    @Test
    fun everyPcScreenShowsItsKeys() {
        rankedSong()
        match.openApp(ranking = script, room = FakeFirebase(clock = System::currentTimeMillis, uid = "u1"))
        composeRule.waitForIdle()
        showsKeys("tela inicial", "Mover", "Escolher")

        click(composeRule.onNodeWithContentDescription("Configurações"), "Geral")
        showsKeys("Configurações", "Abas e opções", "Abas, opções e valores", "Escolher", "Voltar")
        click(composeRule.onNodeWithText("Jogo"), "Calibrar atraso do áudio")
        click(composeRule.onNodeWithText("Calibrar atraso do áudio"), "Começar")
        showsKeys("Calibração", "Mover", "Escolher", "Voltar")
        back("Geral")
        click(composeRule.onNodeWithText("Licenças e privacidade"), "Privacidade")
        showsKeys("Licenças", "Mover", "Escolher", "Voltar")
        back("Geral")
        click(composeRule.onNodeWithText("Geral"), "Nome no ranking")
        click(composeRule.onNodeWithText("Nome no ranking"), "Digite seu nome")
        showsKeys("nome no ranking", "Mover", "Escolher", "Fechar")
        click(composeRule.onNodeWithText("Já tenho um nome em outro aparelho"), "Código do outro aparelho")
        showsKeys("vínculo", "Mover", "Escolher", "Voltar")
        press(Key.Escape, into = isRoot() and hasAnyDescendant(hasText("Código do outro aparelho")))
        composeRule.waitUntilAtLeastOneExists(hasText("Digite seu nome"), TIMEOUT_MS)
        press(Key.Escape, into = isRoot() and hasAnyDescendant(hasText("Digite seu nome")))
        composeRule.waitUntil(TIMEOUT_MS) { !match.exists(hasText("Digite seu nome")) }
        back(home = true)

        click(composeRule.onNodeWithContentDescription("Reportar problema"), "Enviar")
        showsKeys("Reportar", "Mover", "Sair do campo", "Escolher", "Voltar")
        back(home = true)

        click(composeRule.onNodeWithTag(HomeTestTags.RANKING), SONG)
        showsKeys("Ranking", "Mover", "Escolher", "Voltar")
        click(composeRule.onNodeWithText(SONG), "Caio")
        showsKeys("placar", "Mover", "Escolher", "Voltar")
        back(SONG)
        back(home = true)

        click(composeRule.onNodeWithTag(HomeTestTags.SOLO), "+ Mapas")
        showsKeys("Biblioteca", "Mover", "Escolher", "Voltar")
        click(composeRule.onNodeWithText("+ Mapas"), "Pesquisar")
        showsKeys("pesquisa no Encore", "Mover", "Escolher", "Voltar")
        back("+ Mapas")
        back(home = true)

        click(composeRule.onNodeWithTag(HomeTestTags.ROOM), "Criar sala")
        showsKeys("escolha da Sala", "Mover", "Escolher", "Voltar")
        click(composeRule.onNodeWithText("Entrar com código"), "Entrar")
        showsKeys("entrar com código", "Código", "Apagar", "Entrar", "Voltar")

        assertTrue("um símbolo do controle sem controle ligado", !match.exists(hasText("✕") and IN_A_BAR))
    }

    /** Com um controle ligado, as barras mostram o símbolo dele ao lado da tecla ("Esc / ○ Voltar"). */
    @Test
    fun padHintsOnlyWithAPad() {
        match.openApp(pad = TestPad())
        composeRule.waitUntil(TIMEOUT_MS) { match.exists(hasText("✕") and IN_A_BAR) }

        click(composeRule.onNodeWithContentDescription("Configurações"), "Geral")
        composeRule.waitUntil(TIMEOUT_MS) { match.exists(hasText("○") and hasText("Voltar") and IN_A_BAR) }
        assertTrue(match.exists(hasText("✕") and hasText("Escolher") and IN_A_BAR))
    }

    /** As teclas [labels] na barra da tela [screen] (a das telas novas ou a das que já tinham a sua). */
    private fun showsKeys(screen: String, vararg labels: String) {
        composeRule.waitForIdle()
        for (label in labels) {
            assertTrue("$screen sem \"$label\" na barra de teclas", match.exists(hasText(label) and IN_A_BAR))
        }
    }

    private fun click(node: SemanticsNodeInteraction, thenShows: String) {
        composeRule.clickOnWindowThread(node)
        composeRule.waitUntilAtLeastOneExists(hasText(thenShows), TIMEOUT_MS)
        composeRule.waitForIdle()
    }

    /** Esc volta uma tela: até [shows] aparecer ou, com [home], até a tela inicial. */
    private fun back(shows: String? = null, home: Boolean = false) {
        press(Key.Escape)
        val target = if (home) hasTestTag(HomeTestTags.SOLO) else hasText(checkNotNull(shows))
        composeRule.waitUntilAtLeastOneExists(target, TIMEOUT_MS)
        composeRule.waitForIdle()
    }

    private fun press(key: Key, into: SemanticsMatcher = isRoot()) {
        composeRule.runOnUiThread { composeRule.onAllNodes(into).onFirst().performKeyInput { pressKey(key) } }
        composeRule.waitForIdle()
    }

    /** Uma música no ranking com uma linha no placar. */
    private fun rankedSong() {
        val standing = JSONObject().put("jogadores", 1).put("suaPosicao", JSONObject.NULL)
        script.songs = JSONArray().put(
            JSONObject().put("mapa", "mapa-1").put("titulo", SONG).put("artista", "Banda").put("jogadores", 1)
                .put("suaPosicao", JSONObject.NULL)
                .put("tabelas", JSONObject().put("pc_palheta", standing)),
        )
        script.lines = JSONArray().put(line("Caio", 800, "pc_palheta"))
    }

    private companion object {
        const val SONG = "Canção das Teclas"

        /** Dentro de uma barra de teclas: a das telas novas ou a das que já tinham a sua. */
        val IN_A_BAR: SemanticsMatcher = hasAnyAncestor(
            hasTestTag(KeyHintTestTags.BAR) or
                hasTestTag(HomeTestTags.KEY_HINTS) or
                hasTestTag(LibraryTestTags.KEY_HINTS),
        )
    }
}
