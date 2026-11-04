package com.rodrigomachado.riff.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
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
import com.rodrigomachado.riff.core.data.SEPARATED_BACKING
import com.rodrigomachado.riff.core.data.SEPARATED_GUITAR
import com.rodrigomachado.riff.core.data.folder.pcUri
import com.rodrigomachado.riff.core.data.game.ChartAudio
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.QueuedSeparation
import com.rodrigomachado.riff.core.model.SeparationStatus
import com.rodrigomachado.riff.core.separation.Demucs
import com.rodrigomachado.riff.core.separation.DemucsModel
import com.rodrigomachado.riff.core.separation.DemucsModelFactory
import com.rodrigomachado.riff.core.separation.DemucsOutput
import com.rodrigomachado.riff.core.ui.component.DifficultyDialogTestTags
import com.rodrigomachado.riff.core.ui.component.SeparationIndicatorTags
import com.rodrigomachado.riff.desktop.pad.NoPadSource
import com.rodrigomachado.riff.feature.home.HomeTestTags
import com.rodrigomachado.riff.feature.settings.SettingsTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * A guitarra separada por IA no app do PC, com um modelo falso e rápido (metade da música para a guitarra): a pergunta
 * da Biblioteca põe na fila, a palheta do topo mostra o progresso, a partida usa as faixas separadas; "+ Fila", o
 * cancelamento no painel, fechar o Riff no meio (nada pela metade, a música volta à fila e a próxima abertura
 * continua) e a aba "Guitarra IA" das Configurações. As teclas e os cliques chegam na thread da janela.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopSeparationFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val data by lazy { temp.newFolder("dados") }
    private val separations by lazy { File(data, "separacoes") }
    private val graphs = mutableListOf<DesktopGraph>()
    private val model = GatedModel()

    @After
    fun close() {
        model.release()
        graphs.forEach { it.close() }
        DesktopLocale.applyDefault()
    }

    @Test
    fun theQuestionSeparatesAndTheMatchUsesTheSeparatedGuitar() {
        model.blockFromCall(2) // o segundo trecho espera: a palheta mostra o primeiro pronto
        val graph = openApp()
        openLibrary()

        chooseTheSong(FIRST)
        composeRule.waitUntilAtLeastOneExists(hasText(QUESTION), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(SEPARATE))

        // A palheta do topo aparece com o progresso; o painel dela diz quanto já foi.
        composeRule.waitUntilAtLeastOneExists(hasTestTag(SeparationIndicatorTags.ICON), TIMEOUT_MS)
        assertTrue(model.blocked.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        composeRule.waitUntil(TIMEOUT_MS) { (graph.separationScheduler.status.value as? Running)?.progress == 1f / 3 }
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(SeparationIndicatorTags.ICON))
        composeRule.waitUntilAtLeastOneExists(hasText("Separando a guitarra… 33%"), TIMEOUT_MS)
        press(Key.Escape, into = dialogWith(hasText("Separando a guitarra… 33%")))

        model.release()
        val chartId = chartIdOf(graph, FIRST)
        composeRule.waitUntil(TIMEOUT_MS) { runBlocking { chartId in graph.separations.separatedIds.first() } }
        composeRule.waitUntil(TIMEOUT_MS) { !exists(hasTestTag(SeparationIndicatorTags.ICON)) }

        // A partida toca a guitarra separada e o resto à parte (errar corta só a guitarra).
        val audio = runBlocking { graph.trackLoader.load(chartId, Difficulty.EXPERT).audio }
        assertTrue("áudio da partida: $audio", audio is ChartAudio.Stems)
        val stems = audio as ChartAudio.Stems
        val folder = separatedFolders().single()
        fun path(uri: String) = File(uri.removePrefix("file://")).path
        assertEquals(File(folder, SEPARATED_GUITAR).path, path(stems.guitarUri))
        assertEquals(listOf(File(folder, SEPARATED_BACKING).path), stems.backingUris.map(::path))
        assertTrue(File(folder, SEPARATED_GUITAR).length() > 0 && File(folder, SEPARATED_BACKING).length() > 0)
        assertTrue("sobrou pasta temporária", temporaryFolders().isEmpty())
    }

    @Test
    fun plusQueuePutsTwoSongsInOrder() {
        model.blockFromCall(1)
        val graph = openApp()
        openLibrary()
        chooseTheSong(FIRST)
        composeRule.waitUntilAtLeastOneExists(hasText(QUESTION), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(SEPARATE))
        assertTrue(model.blocked.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))

        for (title in listOf(SECOND, THIRD)) {
            chooseTheSong(title)
            composeRule.waitUntilAtLeastOneExists(hasText("Já tem uma separando: esta entra na fila."), TIMEOUT_MS)
            composeRule.clickOnWindowThread(composeRule.onNodeWithText("+ Fila"))
            composeRule.waitUntil(TIMEOUT_MS) { !exists(hasText(QUESTION)) }
        }

        composeRule.waitUntil(TIMEOUT_MS) { graph.separationScheduler.queue.value.size == 2 }
        assertEquals(listOf(SECOND, THIRD), graph.separationScheduler.queue.value.map { it.title })
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(SeparationIndicatorTags.ICON))
        composeRule.waitUntilAtLeastOneExists(hasText("Na fila (2)"), TIMEOUT_MS)
        composeRule.onNodeWithContentDescription("Tirar $SECOND da fila").assertExists()
        composeRule.onNodeWithContentDescription("Tirar $THIRD da fila").assertExists()
        press(Key.Escape, into = dialogWith(hasText("Na fila (2)")))

        model.release()
        composeRule.waitUntil(LONG_TIMEOUT_MS) { runBlocking { graph.separations.separatedIds.first().size == 3 } }
        assertEquals(3, separatedFolders().size)
    }

    /** O painel da palheta, só com o teclado: as setas chegam ao Cancelar, o Enter cancela. */
    @Test
    fun cancellingInThePanelLeavesTheSongAsItWas() {
        model.blockFromCall(1)
        val graph = openApp()
        openLibrary()
        chooseTheSong(FIRST)
        composeRule.waitUntilAtLeastOneExists(hasText(QUESTION), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(SEPARATE))
        assertTrue(model.blocked.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        composeRule.waitUntilAtLeastOneExists(hasTestTag(SeparationIndicatorTags.ICON), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(SeparationIndicatorTags.ICON))
        val panel = dialogWith(hasText("Separando a guitarra… 0%"))
        composeRule.waitUntilAtLeastOneExists(panel, TIMEOUT_MS)

        pressUntilFocused(Key.DirectionDown, hasText("Cancelar"), into = panel)
        press(Key.Enter, into = panel)
        // O modelo termina o trecho da vez; a separação para no seguinte.
        model.release()

        composeRule.waitUntil(TIMEOUT_MS) { graph.separationScheduler.status.value == SeparationStatus.Idle }
        composeRule.waitUntil(TIMEOUT_MS) { temporaryFolders().isEmpty() }
        assertTrue(separatedFolders().isEmpty())
        assertTrue(runBlocking { graph.separations.separatedIds.first().isEmpty() })
        assertTrue(graph.separationScheduler.queue.value.isEmpty())
    }

    @Test
    fun closingInTheMiddleLeavesNothingHalfDoneAndTheNextOpeningContinues() {
        model.blockFromCall(1)
        val graph = openGraph()
        val chartId = chartIdOf(graph, FIRST)
        graph.separationScheduler.enqueue(listOf(QueuedSeparation(chartId, FIRST)))
        assertTrue(model.blocked.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        assertEquals("a pasta temporária enquanto separa", 1, temporaryFolders().size)

        // O trecho da vez acaba logo depois do pedido de fechar (o ONNX Runtime não para no meio de um trecho).
        thread {
            Thread.sleep(CHUNK_END_MS)
            model.release()
        }
        graph.close()
        graphs -= graph

        // A pasta temporária some no fechamento quando a limpeza cabe no prazo do close(); com o PC carregado ela
        // pode sobrar, e a próxima abertura a apaga. Pela metade na pasta definitiva, nunca.
        assertTrue("pasta definitiva pela metade", separatedFolders().isEmpty())

        // Reaberto com os mesmos dados (e o modelo rápido), a fila continua de onde parou.
        val reopened = openGraph(importCharts = false)
        composeRule.waitUntil(LONG_TIMEOUT_MS) { runBlocking { chartId in reopened.separations.separatedIds.first() } }
        assertEquals(1, separatedFolders().size)
        assertTrue("sobrou pasta temporária depois de reabrir", temporaryFolders().isEmpty())
        composeRule.waitUntil(TIMEOUT_MS) { reopened.separationScheduler.queue.value.isEmpty() }
    }

    /**
     * Um Riff só por pasta de dados: o segundo é recusado antes de abrir o banco e a fila (não apaga a pasta
     * temporária da separação em andamento no primeiro); com o primeiro fechado, abre.
     */
    @Test
    fun aSecondRiffOnTheSameDataIsRefusedUntilTheFirstCloses() {
        model.blockFromCall(1)
        val first = openGraph()
        val chartId = chartIdOf(first, FIRST)
        first.separationScheduler.enqueue(listOf(QueuedSeparation(chartId, FIRST)))
        assertTrue(model.blocked.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        val decoded = File(data, "separacao-temp").listFiles().orEmpty().toList()

        assertThrows(RiffAlreadyOpenException::class.java) { openGraph(importCharts = false) }

        assertEquals("a pasta temporária do primeiro", 1, temporaryFolders().size)
        assertTrue("o rascunho do primeiro", decoded.all { it.exists() })
        model.release()
        composeRule.waitUntil(LONG_TIMEOUT_MS) { runBlocking { chartId in first.separations.separatedIds.first() } }
        first.close()
        graphs -= first

        val second = openGraph(importCharts = false)
        assertTrue(runBlocking { chartId in second.separations.separatedIds.first() })
    }

    /** Fechar entre guardar a pasta e anotar no banco deixa uma pasta sem linha: a próxima abertura a apaga. */
    @Test
    fun aSeparationFolderWithoutARowIsDeletedOnOpening() {
        val graph = openGraph()
        val chartId = chartIdOf(graph, FIRST)
        graph.separationScheduler.enqueue(listOf(QueuedSeparation(chartId, FIRST)))
        composeRule.waitUntil(LONG_TIMEOUT_MS) { runBlocking { chartId in graph.separations.separatedIds.first() } }
        graph.close()
        graphs -= graph
        val orphan = File(separations, "0123456789abcdef0123456789abcdef01234567").apply { mkdirs() }
        File(orphan, SEPARATED_GUITAR).writeText("sem linha no banco")

        val reopened = openGraph(importCharts = false)

        composeRule.waitUntil(TIMEOUT_MS) { !orphan.exists() }
        assertTrue("a separação válida ficou", runBlocking { reopened.separations.separatedFiles(chartId) } != null)
        assertEquals(1, separatedFolders().size)
    }

    /** A aba "Guitarra IA" no PC, pelo teclado: o espaço usado e "Apagar separações"; sem "Separar só carregando". */
    @Test
    fun theAiTabShowsTheUsageAndDeletesTheSeparations() {
        val graph = openApp()
        val chartId = chartIdOf(graph, FIRST)
        graph.separationScheduler.enqueue(listOf(QueuedSeparation(chartId, FIRST)))
        composeRule.waitUntil(LONG_TIMEOUT_MS) { runBlocking { chartId in graph.separations.separatedIds.first() } }

        composeRule.clickOnWindowThread(composeRule.onNodeWithContentDescription("Configurações"))
        composeRule.waitUntilAtLeastOneExists(hasText("Geral"), TIMEOUT_MS)
        pressUntilFocused(Key.DirectionDown, hasText("Geral"))
        pressUntilFocused(Key.DirectionRight, hasText(AI_TAB))
        press(Key.Enter)

        composeRule.waitUntilAtLeastOneExists(hasText("1 música · ", substring = true), TIMEOUT_MS)
        composeRule.onNodeWithTag(SettingsTestTags.ASK_ALWAYS).assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.ONLY_CHARGING).assertDoesNotExist()
        composeRule.onNodeWithText("Separar só carregando").assertDoesNotExist()

        pressUntilFocused(Key.DirectionDown, hasText("Apagar separações"))
        press(Key.Enter)
        val confirm = dialogWith(hasText("Apagar as guitarras separadas pela IA?", substring = true))
        composeRule.waitUntilAtLeastOneExists(confirm, TIMEOUT_MS)
        // Os botões ficam lado a lado: a seta para baixo chega ao primeiro (Cancelar), a da direita ao Apagar.
        pressUntilFocused(Key.DirectionDown, hasText("Cancelar"), into = confirm)
        pressUntilFocused(Key.DirectionRight, hasText("Apagar"), into = confirm)
        press(Key.Enter, into = confirm)

        composeRule.waitUntilAtLeastOneExists(hasText("Nenhuma música separada"), TIMEOUT_MS)
        assertTrue(separatedFolders().isEmpty())
    }

    /** A pergunta só com o teclado: as setas chegam ao "Separar" (com o foco à vista) e o Enter põe na fila. */
    @Test
    fun theQuestionIsAnsweredWithTheKeyboardOnly() {
        model.blockFromCall(1)
        val graph = openApp()
        pressUntilFocused(Key.DirectionDown, hasTestTag(HomeTestTags.SOLO))
        press(Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasText(FIRST), TIMEOUT_MS)
        pressUntilFocused(Key.DirectionDown, hasText(FIRST), max = LIBRARY_PRESSES)
        press(Key.Enter)

        val difficulty = dialogWith(hasTestTag(DifficultyDialogTestTags.OPTION))
        composeRule.waitUntilAtLeastOneExists(difficulty, TIMEOUT_MS)
        // A primeira seta foca o Cancelar, embaixo; a opção fica acima dele (o botão dela, dentro da etiqueta).
        val option = hasAnyAncestor(hasTestTag(DifficultyDialogTestTags.OPTION))
        pressUntilFocused(Key.DirectionUp, option, into = difficulty)
        press(Key.Enter, into = difficulty)

        val question = dialogWith(hasText(QUESTION))
        composeRule.waitUntilAtLeastOneExists(question, TIMEOUT_MS)
        // Voltar, Sem separar e Separar lado a lado: a seta para baixo chega ao primeiro, a da direita aos outros.
        pressUntilFocused(Key.DirectionDown, hasText("Voltar"), into = question)
        pressUntilFocused(Key.DirectionRight, hasText(SEPARATE), into = question)
        press(Key.Enter, into = question)

        val chartId = chartIdOf(graph, FIRST)
        composeRule.waitUntil(TIMEOUT_MS) { (graph.separationScheduler.status.value as? Running)?.chartId == chartId }
        composeRule.waitUntil(TIMEOUT_MS) { !exists(hasText(QUESTION)) }
    }

    /** O app com três músicas de 8 s (três trechos do modelo cada), já importadas. */
    private fun openGraph(importCharts: Boolean = true): DesktopGraph {
        val graph = DesktopGraph(
            data,
            menuSounds = MenuSoundPlayer { _, _ -> },
            sfx = SfxPlayerFactory { SilentSfx },
            padSource = NoPadSource,
            encoreTransport = NoInternet,
            separationModels = model,
            // Com a mínima, o Windows deixa a separação parada por segundos enquanto a suíte ocupa todos os núcleos.
            separationThreadPriority = Thread.NORM_PRIORITY,
            // Sem o ranking (desligado, como sem ranking.url): não é o que se testa aqui, e nunca o script real.
            rankingUrl = "",
            rankingTransport = OfflineRankingScript,
        ).also { graphs += it }
        if (importCharts) {
            val charts = temp.newFolder("mapas")
            for (title in listOf(FIRST, SECOND, THIRD)) {
                File(charts, title).apply { mkdirs() }.run {
                    File(this, "notes.chart").writeText(chart(title))
                    writeTone(File(this, "song.wav"), SONG_MS)
                }
            }
            runBlocking {
                graph.libraryFolderStore.setRootUri(pcUri(charts))
                graph.chartImporter.import(pcUri(charts))
            }
        }
        return graph
    }

    private fun openApp(): DesktopGraph {
        val graph = openGraph()
        DesktopLocale.applyDefault()
        composeRule.setContent {
            val real = LocalWindowInfo.current
            val focused = remember(real) { FocusedWindow(real) }
            CompositionLocalProvider(LocalWindowInfo provides focused) { RiffDesktopApp(graph) }
        }
        composeRule.waitForIdle()
        return graph
    }

    private fun openLibrary() {
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(HomeTestTags.SOLO))
        composeRule.waitUntilAtLeastOneExists(hasText(FIRST), TIMEOUT_MS)
    }

    /** A música na Biblioteca → a única dificuldade. */
    private fun chooseTheSong(title: String) {
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(title))
        composeRule.waitUntilAtLeastOneExists(hasTestTag(DifficultyDialogTestTags.OPTION), TIMEOUT_MS)
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(DifficultyDialogTestTags.OPTION))
    }

    private fun chartIdOf(graph: DesktopGraph, title: String) =
        runBlocking { graph.charts.observeCharts().first().single { it.title == title }.id }

    private fun separatedFolders() =
        separations.listFiles().orEmpty().filter { it.isDirectory && !it.name.endsWith(".tmp") }

    private fun temporaryFolders() = separations.listFiles().orEmpty().filter { it.name.endsWith(".tmp") }

    private fun exists(matcher: SemanticsMatcher) = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun dialogWith(content: SemanticsMatcher) = isRoot() and hasAnyDescendant(content)

    /** Na janela [into] (o app; um diálogo aberto é outra janela, com a própria raiz). */
    private fun press(key: Key, into: SemanticsMatcher = APP) {
        composeRule.runOnUiThread { composeRule.onNode(into).performKeyInput { pressKey(key) } }
        composeRule.waitForIdle()
    }

    private fun pressUntilFocused(
        key: Key,
        matcher: SemanticsMatcher,
        max: Int = MAX_PRESSES,
        into: SemanticsMatcher = APP,
    ) {
        repeat(max) {
            if (exists(matcher and isFocused())) return
            press(key, into)
        }
        assertTrue("${matcher.description} não ficou com o foco", exists(matcher and isFocused()))
    }

    /**
     * Metade da música para a guitarra, metade para o baixo (a soma volta a ser a música). A partir da chamada
     * [blockFromCall], espera [release] (o teste segura a separação no meio).
     */
    private class GatedModel : DemucsModelFactory {
        private val calls = AtomicInteger()
        private val gate = CountDownLatch(1)

        @Volatile private var blockFrom = Int.MAX_VALUE
        val blocked = CountDownLatch(1)

        fun blockFromCall(call: Int) {
            blockFrom = call
        }

        fun release() = gate.countDown()

        override fun create(useArena: Boolean): DemucsModel = object : DemucsModel {
            override fun run(mix: FloatArray, spec: FloatArray): DemucsOutput {
                if (calls.incrementAndGet() >= blockFrom) {
                    blocked.countDown()
                    gate.await(GATE_TIMEOUT_S, TimeUnit.SECONDS)
                }
                val out = FloatArray(Demucs.SOURCES * mix.size)
                for (source in listOf(Demucs.GUITAR, BASS)) {
                    for (i in mix.indices) out[source * mix.size + i] = mix[i] / 2
                }
                return DemucsOutput(FloatArray(Demucs.SOURCES * spec.size), out)
            }

            override fun close() = Unit
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val LONG_TIMEOUT_MS = 60_000L
        const val GATE_TIMEOUT_S = 60L
        const val CHUNK_END_MS = 200L
        const val SONG_MS = 8_000
        const val MAX_PRESSES = 10
        const val LIBRARY_PRESSES = 16
        const val BASS = 1
        const val FIRST = "Primeira"
        const val SECOND = "Segunda"
        const val THIRD = "Terceira"
        const val QUESTION = "Separar a guitarra com IA?"
        const val SEPARATE = "Separar"
        const val AI_TAB = "Guitarra IA"

        /** A raiz do app: a que não é a de um diálogo (cada diálogo aberto tem a própria raiz). */
        val APP = isRoot() and !hasAnyDescendant(hasTestTag(DifficultyDialogTestTags.OPTION)) and
            !hasAnyDescendant(hasText(QUESTION))

        fun chart(title: String) = """
            [Song]
            {
              Name = "$title"
              Artist = "Banda"
              Resolution = 192
            }
            [ExpertSingle]
            {
              384 = N 0 0
              576 = N 1 0
            }
        """.trimIndent()
    }
}

private typealias Running = SeparationStatus.Running
