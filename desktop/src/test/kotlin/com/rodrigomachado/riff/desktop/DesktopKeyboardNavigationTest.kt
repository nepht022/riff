package com.rodrigomachado.riff.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.rodrigomachado.riff.core.audio.MenuSoundPlayer
import com.rodrigomachado.riff.core.audio.SfxPlayerFactory
import com.rodrigomachado.riff.core.data.folder.pcUri
import com.rodrigomachado.riff.desktop.pad.NoPadSource
import com.rodrigomachado.riff.feature.calibration.CalibrationTestTags
import com.rodrigomachado.riff.feature.calibration.ClickTrackPlayer
import com.rodrigomachado.riff.feature.home.HomeTestTags
import com.rodrigomachado.riff.feature.library.LibraryTestTags
import com.rodrigomachado.riff.feature.settings.SettingsTestTags
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Os menus do PC só com o teclado: as setas movem o foco (a primeira foca um item da tela), Enter aciona o focado,
 * Esc volta — exceto onde a tela usa o Esc (diálogo aberto, calibração medindo), que não volta duas vezes. As teclas
 * chegam na thread da janela, sem mouse.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopKeyboardNavigationTest {
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
    fun theFirstArrowFocusesTheFirstButtonAndEnterOpensIt() {
        openApp()

        press(Key.DirectionDown)
        // A primeira seta foca o primeiro item da tela (o envelope do canto); a seguinte desce até "Sozinho".
        assertTrue("a primeira seta não focou nada", exists(isFocused()))
        pressUntilFocused(Key.DirectionDown, hasTestTag(HomeTestTags.SOLO))
        press(Key.Enter)

        // A Biblioteca aberta e já lida do banco (fechá-lo no meio da leitura quebraria o fim do teste).
        composeRule.waitUntilAtLeastOneExists(hasText("Nenhuma pasta escolhida"), TIMEOUT_MS)
    }

    @Test
    fun escapeGoesBack() {
        openApp()
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(HomeTestTags.SOLO))
        composeRule.waitUntilAtLeastOneExists(hasText("Biblioteca"), TIMEOUT_MS)

        press(Key.Escape)

        composeRule.waitUntil(TIMEOUT_MS) { !exists(hasText("Biblioteca")) }
        composeRule.onNodeWithTag(HomeTestTags.SOLO).assertExists()
        // Na tela inicial o Esc não faz nada.
        press(Key.Escape)
        composeRule.onNodeWithTag(HomeTestTags.SOLO).assertExists()
    }

    @Test
    fun arrowsWalkTheSettingsTabsAndRows() {
        val graph = openSettings()
        val before = runBlocking { graph.settings.settings.first().menuSounds }

        pressUntilFocused(Key.DirectionDown, hasText("Geral"))
        press(Key.DirectionRight)
        composeRule.onNodeWithText("Sons").assertIsFocused()
        press(Key.DirectionLeft)
        composeRule.onNodeWithText("Geral").assertIsFocused()
        pressUntilFocused(Key.DirectionDown, hasTestTag(SettingsTestTags.MENU_SOUNDS))
        press(Key.Enter)

        composeRule.waitUntil(TIMEOUT_MS) { runBlocking { graph.settings.settings.first().menuSounds } != before }
    }

    @Test
    fun leftAndRightChangeAFocusedSlider() {
        val graph = openSettings()
        pressUntilFocused(Key.DirectionDown, hasText("Geral"))
        press(Key.DirectionRight)
        press(Key.Enter) // abre a aba Sons
        composeRule.waitUntilAtLeastOneExists(hasTestTag(SettingsTestTags.MASTER_VOLUME), TIMEOUT_MS)
        pressUntilFocused(Key.DirectionDown, hasTestTag(SettingsTestTags.MASTER_VOLUME))
        fun volume() = runBlocking { graph.settings.settings.first().masterVolume }
        val full = volume() // começa no máximo: ← primeiro

        press(Key.DirectionLeft)
        composeRule.waitUntil(TIMEOUT_MS) { volume() < full }
        val lowered = volume()
        press(Key.DirectionRight)

        composeRule.waitUntil(TIMEOUT_MS) { volume() > lowered }
        composeRule.onNodeWithTag(SettingsTestTags.MASTER_VOLUME).assertIsFocused()
        val changed = volume()
        press(Key.DirectionDown)
        assertTrue("↓ não saiu do slider", !focused(hasTestTag(SettingsTestTags.MASTER_VOLUME)))
        assertEquals(changed, volume())
    }

    @Test
    fun downScrollsTheLibraryList() {
        openApp(charts = CHART_COUNT)
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(HomeTestTags.SOLO))
        composeRule.waitUntilAtLeastOneExists(hasText(chartTitle(1)), TIMEOUT_MS)
        val target = hasText(chartTitle(TARGET_CHART))
        assertTrue("o mapa ${chartTitle(TARGET_CHART)} já estava na tela", !exists(target))

        pressUntilFocused(Key.DirectionDown, target, max = TARGET_CHART + EXTRA_PRESSES)

        composeRule.onNode(target).assertIsFocused().assertIsDisplayed()
    }

    /** O campo do filtro não prende o foco: ↓ sai dele para a lista (o controle também, pelo direcional). */
    @Test
    fun downLeavesTheLibraryFilter() {
        openFilter()

        press(Key.DirectionDown)

        assertTrue("↓ não saiu do filtro", !focused(hasTestTag(LibraryTestTags.FILTER)))
        pressUntilFocused(Key.DirectionDown, hasText(chartTitle(1)), max = FILTER_TO_LIST_PRESSES)
    }

    /** Esc com o filtro aberto fecha o filtro (e fica na Biblioteca); o seguinte volta à tela inicial. */
    @Test
    fun escapeClosesTheLibraryFilterFirst() {
        openFilter()

        press(Key.Escape)

        composeRule.waitUntil(TIMEOUT_MS) { !exists(hasTestTag(LibraryTestTags.FILTER)) }
        composeRule.onNodeWithText(chartTitle(1)).assertExists()
        composeRule.onNodeWithTag(HomeTestTags.SOLO).assertDoesNotExist()
        press(Key.Escape)
        composeRule.waitUntilAtLeastOneExists(hasTestTag(HomeTestTags.SOLO), TIMEOUT_MS)
    }

    @Test
    fun escapeClosesADialogWithoutGoingBack() {
        openSettings()
        pressUntilFocused(Key.DirectionDown, hasText("Idioma"))
        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasText("English"), TIMEOUT_MS)

        press(Key.Escape, into = LANGUAGE_DIALOG)

        composeRule.waitUntil(TIMEOUT_MS) { !exists(hasText("English")) }
        composeRule.onNodeWithText("Configurações").assertExists()
        composeRule.onNodeWithTag(HomeTestTags.SOLO).assertDoesNotExist()
    }

    @Test
    fun afterTheFocusedItemDisappearsTheNextArrowFindsAnother() {
        openSettings()
        pressUntilFocused(Key.DirectionDown, hasText("Idioma"))
        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasText("English"), TIMEOUT_MS)
        // Dentro do diálogo as setas também andam. Escolher outro idioma refaz as telas: o "Idioma" focado some.
        pressUntilFocused(Key.DirectionDown, hasText("English"), into = LANGUAGE_DIALOG)
        press(Key.Enter, into = LANGUAGE_DIALOG)
        composeRule.waitUntilAtLeastOneExists(hasText("Settings"), TIMEOUT_MS)
        assertTrue("o foco não sumiu com a tela refeita", !exists(isFocused()))

        press(Key.DirectionDown)

        assertTrue("a seta não focou nada", exists(isFocused()))
    }

    @Test
    fun escapeWhileMeasuringStopsTheCalibrationOnly() {
        openSettings()
        pressUntilFocused(Key.DirectionDown, hasText("Geral"))
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.Enter) // abre a aba Jogo
        composeRule.waitUntilAtLeastOneExists(hasText("Calibrar atraso do áudio"), TIMEOUT_MS)
        pressUntilFocused(Key.DirectionDown, hasText("Calibrar atraso do áudio"))
        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasText("Começar"), TIMEOUT_MS)
        pressUntilFocused(Key.DirectionDown, hasText("Começar"))
        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasTestTag(CalibrationTestTags.TAP_AREA), TIMEOUT_MS)
        composeRule.waitForIdle() // a área pega o foco do teclado

        press(Key.Escape)

        composeRule.waitUntilAtLeastOneExists(hasText("Começar"), TIMEOUT_MS)
        composeRule.onNodeWithTag(CalibrationTestTags.TAP_AREA).assertDoesNotExist()
        composeRule.onNodeWithText("Geral").assertDoesNotExist()
        press(Key.Escape)
        composeRule.waitUntilAtLeastOneExists(hasText("Geral"), TIMEOUT_MS)
    }

    /** O app na tela inicial; com [charts] mapas falsos já importados ("Mapa 01", "Mapa 02"…). */
    private fun openApp(charts: Int = 0): DesktopGraph {
        val graph = DesktopGraph(
            temp.newFolder("dados"),
            menuSounds = MenuSoundPlayer { _, _ -> },
            sfx = SfxPlayerFactory { SilentSfx },
            // A medição da calibração só acaba quando o teste sai.
            clickTrack = ClickTrackPlayer { awaitCancellation() },
            padSource = NoPadSource,
            // Sem o ranking (desligado, como sem ranking.url): não é o que se testa aqui, e nunca o script real.
            rankingUrl = "",
            rankingTransport = OfflineRankingScript,
        ).also { graph = it }
        if (charts > 0) {
            val folder = temp.newFolder("mapas")
            for (number in 1..charts) {
                File(folder, chartTitle(number)).apply { mkdirs() }.run {
                    File(this, "notes.chart").writeText(chart(chartTitle(number)))
                    File(this, "song.ogg").writeBytes(ByteArray(10))
                }
            }
            runBlocking {
                graph.libraryFolderStore.setRootUri(pcUri(folder))
                graph.chartImporter.import(pcUri(folder))
            }
        }
        DesktopLocale.applyDefault()
        composeRule.setContent {
            // A janela do teste não disputa o foco do sistema: sem isso a calibração pararia como num Alt+Tab.
            val real = LocalWindowInfo.current
            val focused = remember(real) { FocusedWindow(real) }
            CompositionLocalProvider(LocalWindowInfo provides focused) { RiffDesktopApp(graph) }
        }
        composeRule.waitForIdle()
        return graph
    }

    /** A Biblioteca com mapas, o filtro aberto pela lupa e o campo dele com o foco (digitando). */
    private fun openFilter() {
        openApp(charts = FILTER_CHARTS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(HomeTestTags.SOLO))
        composeRule.waitUntilAtLeastOneExists(hasText(chartTitle(1)), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithContentDescription("Pesquisar na biblioteca"))
        composeRule.waitUntilAtLeastOneExists(hasTestTag(LibraryTestTags.FILTER), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(LibraryTestTags.FILTER))
        composeRule.waitUntil(TIMEOUT_MS) { focused(hasTestTag(LibraryTestTags.FILTER)) }
    }

    /** O app aberto e as Configurações pelo ícone do canto (o caminho até elas não é o que se testa). */
    private fun openSettings(): DesktopGraph {
        val graph = openApp()
        composeRule.clickOnWindowThread(composeRule.onNodeWithContentDescription("Configurações"))
        composeRule.waitUntilAtLeastOneExists(hasText("Geral"), TIMEOUT_MS)
        composeRule.waitForIdle()
        return graph
    }

    /** Na janela [into] (o app; um diálogo aberto é outra janela, com a própria raiz). */
    private fun press(key: Key, into: SemanticsMatcher = APP) {
        composeRule.runOnUiThread { composeRule.onNode(into).performKeyInput { pressKey(key) } }
        composeRule.waitForIdle()
    }

    /** Aperta [key] até [matcher] ficar com o foco (no máximo [max] vezes). */
    private fun pressUntilFocused(
        key: Key,
        matcher: SemanticsMatcher,
        max: Int = MAX_PRESSES,
        into: SemanticsMatcher = APP,
    ) {
        repeat(max) {
            if (focused(matcher)) return
            press(key, into)
        }
        assertTrue("${matcher.description} não ficou com o foco", focused(matcher))
    }

    private fun exists(matcher: SemanticsMatcher) = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun focused(matcher: SemanticsMatcher) = exists(matcher and isFocused())

    private companion object {
        const val TIMEOUT_MS = 15_000L

        /** A raiz do diálogo de idioma (o do "Fechar") e a do app (a que não tem o diálogo). */
        val LANGUAGE_DIALOG = isRoot() and hasAnyDescendant(hasText("Fechar"))
        val APP = isRoot() and !hasAnyDescendant(hasText("Fechar"))
        const val MAX_PRESSES = 8
        const val CHART_COUNT = 30
        const val TARGET_CHART = 25
        const val FILTER_CHARTS = 3

        /** Entre o filtro e o primeiro mapa, as ações da lista (Procurar de novo, Trocar pasta, Selecionar). */
        const val FILTER_TO_LIST_PRESSES = 4

        /** Os botões acima da lista (Voltar, Pesquisar, a lupa, as ações da lista). */
        const val EXTRA_PRESSES = 10

        fun chartTitle(number: Int) = "Mapa %02d".format(number)

        fun chart(title: String) = """
            [Song]
            {
              Name = "$title"
              Artist = "Banda"
            }
            [ExpertSingle]
            {
              192 = N 0 0
            }
        """.trimIndent()
    }
}
