package com.rodrigomachado.riff.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.PLAY_AGAIN
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.STAGE
import com.rodrigomachado.riff.desktop.DesktopMatch.Companion.TIMEOUT_MS
import com.rodrigomachado.riff.desktop.FakeRankingScript.Companion.line
import com.rodrigomachado.riff.feature.home.HomeTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * O ranking no app do PC, com o script falso (nunca o de verdade): o nome (na primeira abertura, pelo Resultado e
 * pelas Configurações, com o vínculo por código), as partidas na tabela do modo em que foram jogadas ("Tocar com
 * palheta" de quando começaram), a tela Ranking na tabela do modo atual com a troca de tabela, tudo só com o teclado,
 * e o ranking desligado sem `ranking.url`. Partidas de verdade, como em [DesktopGameplayFlowTest].
 */
@OptIn(ExperimentalTestApi::class)
class DesktopRankingFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val match = DesktopMatch(composeRule, temp)
    private val script = FakeRankingScript()

    @After
    fun close() = match.close()

    @Test
    fun theFirstOpeningAsksForTheNameAndSavesIt() {
        match.openApp(ranking = script, askForName = true)
        composeRule.waitUntilAtLeastOneExists(hasText(NAME_TITLE), TIMEOUT_MS)

        // Só o teclado: a primeira seta foca o campo, o nome escrito, ↓ até os botões, → até Salvar e Enter.
        press(Key.DirectionDown, into = DIALOG)
        type("Rodrigo")
        pressUntilFocused(Key.DirectionDown, hasText(LATER), into = DIALOG)
        press(Key.DirectionRight, into = DIALOG)
        assertTrue("→ não chegou a $SAVE", focused(hasText(SAVE)))
        press(Key.Enter, into = DIALOG)

        composeRule.waitUntil(TIMEOUT_MS) { !match.exists(hasText(NAME_TITLE)) }
        assertEquals("Rodrigo", runBlocking { match.graph.ranking.player.first().name })
        val reserve = script.requests.single { it.getString("acao") == "reservar" }
        assertEquals("Rodrigo", reserve.getString("nome"))
    }

    /**
     * Sem nome, a partida espera na fila e o Resultado convida a escolher um; com o nome, ela vai na tabela "PC com
     * palheta". Desligar a palheta: a partida seguinte vai na "PC sem palheta", e o Resultado mostra a posição.
     */
    @Test
    fun eachMatchGoesToTheTableOfItsMode() {
        match.openApp(ranking = script)
        match.openLibrary()
        match.startTheSong()
        match.playUntil(hasText(NO_NAME))
        assertTrue("enviou sem nome: ${script.requests}", script.submissions.isEmpty())

        composeRule.clickOnWindowThread(composeRule.onNodeWithText(NO_NAME))
        composeRule.waitUntilAtLeastOneExists(hasText(NAME_TITLE), TIMEOUT_MS)
        type("Rodrigo")
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(SAVE))
        composeRule.waitUntil(TIMEOUT_MS) { script.submissions.isNotEmpty() }
        assertEquals(setOf("pc_palheta"), tables())

        changeSettings { it.copy(controls = it.controls.copy(strum = false)) }
        composeRule.mainClock.autoAdvance = false
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(PLAY_AGAIN))
        match.playInTheMatchUntil("a partida") { match.exists(STAGE) }
        match.playUntil(hasText(PLAY_AGAIN))

        composeRule.waitUntil(TIMEOUT_MS) { "pc_sem_palheta" in tables() }
        composeRule.waitUntilAtLeastOneExists(hasText("Ranking: 1º de 1"), TIMEOUT_MS)
        assertEquals(setOf("pc_palheta", "pc_sem_palheta"), tables())
    }

    /** A tabela é a das configurações de quando a partida começou: desligar a palheta no meio não a muda. */
    @Test
    fun changingTheModeDuringTheMatchKeepsItsTable() {
        match.openApp(ranking = script)
        runBlocking { match.graph.ranking.register("Rodrigo") }
        match.openLibrary()
        match.startTheSong()

        changeSettings { it.copy(controls = it.controls.copy(strum = false)) }
        match.playUntil(hasText(PLAY_AGAIN))

        composeRule.waitUntil(TIMEOUT_MS) { script.submissions.isNotEmpty() }
        assertEquals(setOf("pc_palheta"), tables())
    }

    @Test
    fun theRankingOpensOnTheTableOfTheCurrentModeAndSwitches() {
        rankedSong()
        match.openApp(ranking = script)
        changeSettings { it.copy(controls = it.controls.copy(strum = false)) }

        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(HomeTestTags.RANKING))
        composeRule.waitUntilAtLeastOneExists(hasText(SONG), TIMEOUT_MS)
        composeRule.onNodeWithText(NO_STRUM).assertIsSelected()
        composeRule.onNodeWithText(STRUM).assertIsNotSelected()
        composeRule.onNodeWithText("2 jogadores com pontuação", substring = true).assertExists()
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(PHONE))
        composeRule.onNodeWithText("Ninguém nesta tabela ainda").assertExists()
        composeRule.onNodeWithText(SONG).assertDoesNotExist()
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(STRUM))
        composeRule.onNodeWithText("1 jogador com pontuação", substring = true).assertExists()

        // O placar abre na tabela escolhida na lista.
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(SONG))
        composeRule.waitUntilAtLeastOneExists(hasText("Caio"), TIMEOUT_MS)
        composeRule.onNodeWithText(STRUM).assertIsSelected()
        composeRule.onNodeWithText("Bia").assertDoesNotExist()
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(NO_STRUM))
        composeRule.waitUntilAtLeastOneExists(hasText("Bia"), TIMEOUT_MS)
        composeRule.onNodeWithText("Caio").assertDoesNotExist()
    }

    /** Da tela inicial ao placar e de volta sem o mouse: setas, Enter nos chips de tabela e Esc. */
    @Test
    fun theBoardWorksWithTheKeyboardOnly() {
        rankedSong()
        match.openApp(ranking = script)
        composeRule.waitForIdle()

        // O Ranking é o terceiro quadro da tela inicial do PC: ↓ até os quadros, → até ele.
        pressUntilFocused(Key.DirectionDown, hasTestTag(HomeTestTags.SOLO))
        pressUntilFocused(Key.DirectionRight, hasTestTag(HomeTestTags.RANKING))
        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasText(SONG), TIMEOUT_MS)
        // Com palheta, o padrão de fábrica do PC.
        composeRule.onNodeWithText(STRUM).assertIsSelected()
        pressUntilFocused(Key.DirectionDown, hasText(SONG))
        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasText("Caio"), TIMEOUT_MS)

        pressUntilFocused(Key.DirectionDown, hasText(PHONE))
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        assertTrue("→ não chegou a $NO_STRUM", focused(hasText(NO_STRUM)))
        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasText("Bia"), TIMEOUT_MS)
        composeRule.onNodeWithText(NO_STRUM).assertIsSelected()
        press(Key.DirectionLeft)
        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasText("Caio"), TIMEOUT_MS)
        composeRule.onNodeWithText(STRUM).assertIsSelected()

        press(Key.Escape)
        composeRule.waitUntil(TIMEOUT_MS) { !match.exists(hasText("Caio")) }
        composeRule.onNodeWithText(SONG).assertExists()
        press(Key.Escape)
        composeRule.waitUntilAtLeastOneExists(hasTestTag(HomeTestTags.SOLO), TIMEOUT_MS)
    }

    /** "Nome no ranking" nas Configurações → "Já tenho um nome em outro aparelho": código errado, depois o certo. */
    @Test
    fun theSettingsLinkThisPcToANameByCode() {
        match.openApp(ranking = script)
        openSettings()

        // As abas numa coluna à esquerda: da aba Geral, → passa às opções dela.
        pressUntilFocused(Key.DirectionDown, hasText("Geral"))
        press(Key.DirectionRight)
        pressUntilFocused(Key.DirectionDown, hasText(SETTINGS_NAME))
        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasText(NAME_TITLE), TIMEOUT_MS)
        pressUntilFocused(Key.DirectionDown, hasText(HAVE_A_NAME), into = DIALOG)
        press(Key.Enter, into = DIALOG)
        composeRule.waitUntilAtLeastOneExists(hasText(CODE_TITLE), TIMEOUT_MS)

        press(Key.DirectionDown, into = DIALOG)
        type("XYZ999")
        pressUntilFocused(Key.DirectionDown, hasText(BACK), into = DIALOG)
        press(Key.DirectionRight, into = DIALOG)
        assertTrue("→ não chegou a $LINK", focused(hasText(LINK)))
        press(Key.Enter, into = DIALOG)
        composeRule.waitUntilAtLeastOneExists(hasText("Código errado ou vencido"), TIMEOUT_MS)

        composeRule.runOnUiThread { composeRule.onNode(hasSetTextAction()).performTextClearance() }
        type("abc-234")
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(LINK))

        composeRule.waitUntil(TIMEOUT_MS) { !match.exists(hasText(CODE_TITLE)) }
        composeRule.waitUntilAtLeastOneExists(hasText(FakeRankingScript.LINKED_NAME), TIMEOUT_MS)
        val codes = script.requests.filter { it.getString("acao") == "vincular" }.map { it.getString("codigo") }
        assertEquals(listOf("XYZ999", FakeRankingScript.LINK_CODE), codes)
        assertEquals(FakeRankingScript.LINKED_NAME, runBlocking { match.graph.ranking.player.first().name })
    }

    /** Esc no código volta ao nome; no nome (pelas Configurações), fecha sem trocar nada. */
    @Test
    fun escapeStepsBackOutOfTheNameDialog() {
        match.openApp(ranking = script)
        openSettings()
        // As abas numa coluna à esquerda: da aba Geral, → passa às opções dela.
        pressUntilFocused(Key.DirectionDown, hasText("Geral"))
        press(Key.DirectionRight)
        pressUntilFocused(Key.DirectionDown, hasText(SETTINGS_NAME))
        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasText(NAME_TITLE), TIMEOUT_MS)
        pressUntilFocused(Key.DirectionDown, hasText(HAVE_A_NAME), into = DIALOG)
        press(Key.Enter, into = DIALOG)
        composeRule.waitUntilAtLeastOneExists(hasText(CODE_TITLE), TIMEOUT_MS)

        press(Key.Escape, into = DIALOG)

        composeRule.waitUntilAtLeastOneExists(hasText(NAME_TITLE), TIMEOUT_MS)
        press(Key.Escape, into = DIALOG)
        composeRule.waitUntil(TIMEOUT_MS) { !match.exists(hasText(NAME_TITLE)) }
        composeRule.onNodeWithText(SETTINGS_NAME, substring = true).assertExists()
        assertTrue("pediu algo ao script: ${script.requests}", script.requests.isEmpty())
    }

    /** Sem `ranking.url`, como no celular: sem pedido do nome; a tela Ranking avisa; sem a linha do nome nem envio. */
    @Test
    fun withoutTheRankingUrlTheRankingIsOff() {
        match.openApp(ranking = script, rankingUrl = "", askForName = true)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(NAME_TITLE).assertDoesNotExist()

        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(HomeTestTags.RANKING))
        composeRule.waitUntilAtLeastOneExists(hasText("Ranking não configurado"), TIMEOUT_MS)
        press(Key.Escape)
        openSettings()
        composeRule.onNodeWithText(SETTINGS_NAME, substring = true).assertDoesNotExist()
        press(Key.Escape)
        match.openLibrary()
        match.startTheSong()
        match.playUntil(hasText(PLAY_AGAIN))

        composeRule.onNodeWithText(NO_NAME).assertDoesNotExist()
        composeRule.onNodeWithText("Ver ranking").assertDoesNotExist()
        assertTrue("pediu algo ao script: ${script.requests}", script.requests.isEmpty())
    }

    /** Uma música no ranking com gente nas duas tabelas do PC; o placar com uma linha em cada tabela. */
    private fun rankedSong() {
        val standing = { players: Int -> JSONObject().put("jogadores", players).put("suaPosicao", JSONObject.NULL) }
        script.songs = JSONArray().put(
            JSONObject().put("mapa", "mapa-1").put("titulo", SONG).put("artista", "Banda").put("jogadores", 3)
                .put("suaPosicao", JSONObject.NULL)
                .put("tabelas", JSONObject().put("pc_sem_palheta", standing(2)).put("pc_palheta", standing(1))),
        )
        script.lines = JSONArray()
            .put(line("Bia", 900, "pc_sem_palheta"))
            .put(line("Caio", 800, "pc_palheta"))
            .put(line("Duda", 700, "celular"))
    }

    private fun tables(): Set<String> = script.submissions.map { it.getString("tabela") }.toSet()

    private fun changeSettings(transform: (Settings) -> Settings) {
        runBlocking { match.graph.settings.update(transform) }
        composeRule.waitForIdle()
    }

    private fun openSettings() {
        composeRule.clickOnWindowThread(composeRule.onNodeWithContentDescription("Configurações"))
        composeRule.waitUntilAtLeastOneExists(hasText("Geral"), TIMEOUT_MS)
        composeRule.waitForIdle()
    }

    /** No campo do diálogo (o único campo de texto na tela). */
    private fun type(text: String) {
        composeRule.runOnUiThread { composeRule.onNode(hasSetTextAction()).performTextInput(text) }
        composeRule.waitForIdle()
    }

    /** Na janela [into] (o app; o diálogo do nome é outra janela, com a própria raiz). */
    private fun press(key: Key, into: SemanticsMatcher = APP) {
        composeRule.runOnUiThread { composeRule.onNode(into).performKeyInput { pressKey(key) } }
        composeRule.waitForIdle()
    }

    private fun pressUntilFocused(key: Key, matcher: SemanticsMatcher, into: SemanticsMatcher = APP) {
        repeat(MAX_PRESSES) {
            if (focused(matcher)) return
            press(key, into)
        }
        assertTrue("${matcher.description} não ficou com o foco (está em ${focusedNow()})", focused(matcher))
    }

    /** O que tem o foco agora (as mensagens de falha dizem onde a seta parou). */
    private fun focusedNow(): List<String> = composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().map { node ->
        node.config.toString()
    }

    private fun focused(matcher: SemanticsMatcher) = match.exists(matcher and isFocused())

    private companion object {
        const val NAME_TITLE = "Digite seu nome"
        const val CODE_TITLE = "Código do outro aparelho"
        const val SAVE = "Salvar"
        const val LATER = "Agora não"
        const val BACK = "Voltar"
        const val LINK = "Vincular"
        const val HAVE_A_NAME = "Já tenho um nome em outro aparelho"
        const val NO_NAME = "Escolha um nome para entrar no ranking"
        const val SETTINGS_NAME = "Nome no ranking"
        const val SONG = "Canção do Ranking"
        const val PHONE = "Celular"
        const val STRUM = "PC com palheta"
        const val NO_STRUM = "PC sem palheta"
        const val MAX_PRESSES = 12

        val DIALOG = isRoot() and (hasAnyDescendant(hasText(NAME_TITLE)) or hasAnyDescendant(hasText(CODE_TITLE)))
        val APP = isRoot() and !hasAnyDescendant(hasText(NAME_TITLE)) and !hasAnyDescendant(hasText(CODE_TITLE))
    }
}
