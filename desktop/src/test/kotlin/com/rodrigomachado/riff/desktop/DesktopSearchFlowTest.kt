package com.rodrigomachado.riff.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import com.rodrigomachado.riff.core.chart.sng.SngWriter
import com.rodrigomachado.riff.core.encore.HttpResponse
import com.rodrigomachado.riff.core.encore.HttpTransport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import javax.swing.SwingUtilities

/**
 * A pesquisa no Encore no PC, como no celular: Biblioteca → Pesquisar → baixar para "Baixados pelo Riff" → o mapa na
 * Biblioteca e na partida. O Encore é falso (os testes nunca usam a rede).
 */
@OptIn(ExperimentalTestApi::class)
class DesktopSearchFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val match = DesktopMatch(composeRule, temp)

    @After
    fun close() = match.close()

    @Test
    fun searchingAndDownloadingPutsTheChartInTheLibrary() {
        val encore = FakeEncore(FakeChart("a1b2", "Teste do Encore", "Banda do Encore"))
        openSearch(encore)

        search("teste")
        waitForResults()
        composeRule.clickOnWindowThread(composeRule.onNodeWithText("Baixar"))

        composeRule.waitUntilAtLeastOneExists(hasText("Jogar"), DesktopMatch.TIMEOUT_MS)
        assertEquals(listOf("Banda do Encore - Teste do Encore.sng"), downloaded().map { it.name })
        assertFalse("a busca rodou na thread da janela", encore.onWindowThread)
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(DesktopMatch.BACK))
        composeRule.waitUntilAtLeastOneExists(hasText("Teste do Encore"), DesktopMatch.TIMEOUT_MS)
        composeRule.onNodeWithText(DesktopMatch.TITLE).assertExists()
    }

    @Test
    fun aDownloadedChartPlays() {
        openSearch(FakeEncore(FakeChart("a1b2", "Teste do Encore", "Banda do Encore")))
        search("teste")
        waitForResults()
        composeRule.clickOnWindowThread(composeRule.onNodeWithText("Baixar"))
        composeRule.waitUntilAtLeastOneExists(hasText("Jogar"), DesktopMatch.TIMEOUT_MS)

        composeRule.clickOnWindowThread(composeRule.onNodeWithText("Jogar"))
        match.chooseTheOnlyDifficulty()

        assertTrue(match.exists(DesktopMatch.STAGE))
    }

    @Test
    fun withoutTheInternetTheSearchSaysSo() {
        val encore = FakeEncore(failure = IOException("sem rede"))
        openSearch(encore)

        search("teste")

        composeRule.waitUntilAtLeastOneExists(hasText("Não deu para falar com o Encore."), DesktopMatch.TIMEOUT_MS)
        assertFalse("a busca rodou na thread da janela", encore.onWindowThread)
        // A janela segue respondendo: Voltar leva à Biblioteca.
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(DesktopMatch.BACK))
        composeRule.waitUntilAtLeastOneExists(hasText(DesktopMatch.TITLE), DesktopMatch.TIMEOUT_MS)
    }

    @Test
    fun aTitleWithCharactersWindowsForbidsStillDownloads() {
        openSearch(
            FakeEncore(
                FakeChart("a1b2", "AC/DC: Back In Black?", "AC/DC"),
                // O Windows lê o nome só até o primeiro ponto: "Nul. - CON.sng" seria o NUL (o dispositivo).
                FakeChart("c3d4", "CON", "Nul."),
            ),
        )
        search("teste")
        waitForResults()

        // O segundo antes: o primeiro continua o primeiro, com a lista já refeita ou não.
        composeRule.clickOnWindowThread(composeRule.onAllNodesWithText("Baixar")[1])
        composeRule.clickOnWindowThread(composeRule.onAllNodesWithText("Baixar")[0])

        composeRule.waitUntil(DesktopMatch.TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Jogar").fetchSemanticsNodes().size == 2
        }
        val names = downloaded().map { it.name }
        assertEquals(2, names.size)
        names.forEach { assertTrue("$it não vale no Windows", windowsAccepts(it)) }
    }

    @Test
    fun keyboardOnlySearch() {
        openSearch(FakeEncore(FakeChart("a1b2", "Teste do Encore", "Banda do Encore")))
        val field = hasSetTextAction()

        pressUntilFocused(Key.DirectionDown, field)
        composeRule.runOnUiThread { composeRule.onNode(field).performTextInput("teste") }
        press(Key.Enter)

        waitForResults()
        press(Key.DirectionDown)
        assertFalse("↓ não saiu do campo", focused(field))
        pressUntilFocused(Key.DirectionDown, hasText("Baixar"))
        pressUntilFocused(Key.DirectionUp, field)
        press(Key.Escape)
        composeRule.waitUntilAtLeastOneExists(hasText(DesktopMatch.TITLE), DesktopMatch.TIMEOUT_MS)
    }

    /** O app com a pasta de mapas e a pesquisa aberta pela Biblioteca. */
    private fun openSearch(encore: HttpTransport) {
        match.openApp(encore = encore)
        match.openLibrary()
        composeRule.clickOnWindowThread(composeRule.onNodeWithText("Pesquisar Mapas"))
        composeRule.waitUntilAtLeastOneExists(hasText("Música ou artista"), DesktopMatch.TIMEOUT_MS)
    }

    /** Digita [query] no campo e pesquisa pelo Enter. */
    private fun search(query: String) {
        composeRule.runOnUiThread { composeRule.onNode(hasSetTextAction()).performTextInput(query) }
        press(Key.Enter)
    }

    private fun waitForResults() = composeRule.waitUntilAtLeastOneExists(hasText("Baixar"), DesktopMatch.TIMEOUT_MS)

    private fun downloaded(): List<File> =
        File(temp.root, "mapas/Baixados pelo Riff").listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }

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

    private fun focused(matcher: SemanticsMatcher) = match.exists(matcher and isFocused())

    private companion object {
        const val MAX_PRESSES = 10
        val WINDOWS_FORBIDS = Regex("""[\\/:*?"<>|\x00-\x1F]""")
        val RESERVED = setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }

        /** As regras do Windows para um nome de arquivo (caracteres, fim com ponto ou espaço e nomes reservados). */
        fun windowsAccepts(name: String): Boolean = !WINDOWS_FORBIDS.containsMatchIn(name) &&
            !name.endsWith('.') && !name.endsWith(' ') &&
            name.substringBefore('.').trimEnd().uppercase() !in RESERVED
    }
}

/** Um mapa do Encore falso: um pacote com um mapa de duas notas e um tom de 2 s. */
private class FakeChart(val md5: String, val name: String, val artist: String)

/**
 * O Encore falso: a busca devolve [charts] numa página; o download, o pacote de cada um (inteiro ou o pedaço pedido).
 * Com [failure], tudo falha como sem internet. Anota se alguma conversa veio da thread da janela.
 */
private class FakeEncore(vararg val charts: FakeChart, val failure: IOException? = null) : HttpTransport {
    @Volatile var onWindowThread = false

    override suspend fun post(url: String, json: String): HttpResponse {
        check()
        val data = charts.joinToString(",") {
            """{"name": "${it.name.json()}", "artist": "${it.artist.json()}", "charter": "charter", "diff_guitar": 2,
                "md5": "${it.md5}", "hasVideoBackground": false, "song_length": 2000,
                "notesData": {"noteCounts": [{"instrument": "guitar", "difficulty": "expert", "count": 2}]}}"""
        }
        val body = """{"found": ${charts.size}, "out_of": ${charts.size}, "page": 1, "data": [$data]}"""
        return HttpResponse(201, null, body.byteInputStream())
    }

    override suspend fun get(url: String, range: LongRange?): HttpResponse {
        check()
        val md5 = url.substringAfterLast('/').substringBefore('.')
        val chart = charts.single { it.md5 == md5 }
        val all = sng(chart)
        val bytes = if (range == null) all else all.copyOfRange(0, minOf(all.size, range.last.toInt() + 1))
        return HttpResponse(if (range == null) 200 else 206, bytes.size.toLong(), bytes.inputStream())
    }

    private fun check() {
        if (SwingUtilities.isEventDispatchThread()) onWindowThread = true
        failure?.let { throw it }
    }

    private fun sng(chart: FakeChart): ByteArray {
        val tone = File.createTempFile("tom", ".wav").apply { deleteOnExit() }
        writeTone(tone, SONG_MS)
        return SngWriter.write(
            mapOf("name" to chart.name, "artist" to chart.artist),
            mapOf("notes.chart" to chartText(chart).toByteArray(), "song.wav" to tone.readBytes()),
        )
    }

    private fun chartText(chart: FakeChart) = """
        [Song]
        {
          Name = "${chart.name}"
          Artist = "${chart.artist}"
          Resolution = 192
        }
        [ExpertSingle]
        {
          384 = N 0 0
          576 = N 1 0
        }
    """.trimIndent()

    private fun String.json() = replace("\\", "\\\\").replace("\"", "\\\"")

    private companion object {
        const val SONG_MS = 2_000
    }
}
