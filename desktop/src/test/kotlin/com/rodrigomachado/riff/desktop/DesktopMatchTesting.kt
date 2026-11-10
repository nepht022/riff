package com.rodrigomachado.riff.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.KeyInjectionScope
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.rodrigomachado.riff.core.audio.AudioSink
import com.rodrigomachado.riff.core.audio.AudioSinkFactory
import com.rodrigomachado.riff.core.audio.FfmpegPcmSource
import com.rodrigomachado.riff.core.audio.MenuSoundPlayer
import com.rodrigomachado.riff.core.audio.SfxPlayer
import com.rodrigomachado.riff.core.audio.SfxPlayerFactory
import com.rodrigomachado.riff.core.data.folder.FileRandomReadOpener
import com.rodrigomachado.riff.core.data.folder.pcUri
import com.rodrigomachado.riff.core.data.ranking.RankingTransport
import com.rodrigomachado.riff.core.encore.HttpResponse
import com.rodrigomachado.riff.core.encore.HttpTransport
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.ScoreRecord
import com.rodrigomachado.riff.core.room.testing.FakeFirebase
import com.rodrigomachado.riff.core.ui.component.DifficultyDialogTestTags
import com.rodrigomachado.riff.desktop.pad.NoPadSource
import com.rodrigomachado.riff.desktop.pad.PadSource
import com.rodrigomachado.riff.feature.gameplay.GameplayTestTags
import com.rodrigomachado.riff.feature.home.HomeTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.rules.TemporaryFolder
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.UnknownHostException
import kotlin.math.PI
import kotlin.math.sin

/**
 * O app do PC aberto com um mapa de duas notas (1 s e 1,5 s) e 2 s de tom, já importado, para os testes que jogam
 * uma partida de verdade (o FFmpeg decodifica o tom da pasta). O som sai numa saída falsa que anda no ritmo do relógio
 * (o CI não tem placa de som), e as teclas são as de verdade, entregues na thread da janela.
 */
@OptIn(ExperimentalTestApi::class)
internal class DesktopMatch(private val composeRule: ComposeContentTestRule, private val temp: TemporaryFolder) {
    private val sinks = mutableListOf<RealTimeSink>()
    private var opened: DesktopGraph? = null

    val graph: DesktopGraph get() = checkNotNull(opened) { "o app não foi aberto" }

    /** Fecha o app (uma vez só: o teste pode fechar antes do fim, e o @After fecha de novo). */
    fun close() {
        opened?.close()
        opened = null
    }

    /**
     * O app na tela inicial, com a pasta de mapas já importada; com [pad], o controle já lido, e as teclas que ele
     * manda aos menus por [sendMenuKey]. A pesquisa fala com o Encore por [encore] (nos testes, nunca a rede). O
     * ranking fica ligado e fala com o script falso [ranking] (o padrão: sem internet) em [rankingUrl] (vazio:
     * desligado, como sem `ranking.url`); sem [askForName], o pedido do nome da primeira abertura já foi respondido com
     * "Agora não" (como a pergunta da separação: o pedido do nome tem os seus testes). A Sala fala com o Firebase falso
     * [room]; sem ele, fica sem configuração ("em breve"), e um Firebase falso sem internet garante que nada sai.
     */
    fun openApp(
        pad: PadSource = NoPadSource,
        sendMenuKey: ((awtKeyCode: Int) -> Unit)? = null,
        encore: HttpTransport = NoInternet,
        ranking: RankingTransport = OfflineRankingScript,
        rankingUrl: String = TEST_RANKING_URL,
        askForName: Boolean = false,
        room: FakeFirebase? = null,
    ): DesktopGraph {
        val charts = temp.newFolder("mapas")
        File(charts, "Tom do PC").apply { mkdirs() }.run {
            File(this, "notes.chart").writeText(CHART)
            writeTone(File(this, "song.wav"), SONG_MS)
        }
        LoadedFfmpeg.ensure()
        val audioOut = AudioSinkFactory { rate -> RealTimeSink(rate).also { synchronized(sinks) { sinks += it } } }
        val graph = DesktopGraph(
            temp.newFolder("dados"),
            menuSounds = MenuSoundPlayer { _, _ -> },
            audioOut = audioOut,
            sfx = SfxPlayerFactory { SilentSfx },
            padSource = pad,
            encoreTransport = encore,
            rankingUrl = rankingUrl,
            rankingTransport = ranking,
            roomApiKey = if (room != null) FakeFirebase.API_KEY else "",
            roomDbUrl = if (room != null) FakeFirebase.DB_URL else "",
            roomHttp = room ?: FakeFirebase(System::currentTimeMillis).apply { online = false },
        ).also { opened = it }
        if (!askForName) graph.ranking.askLater()
        graph.gamepad.start()
        runBlocking {
            graph.libraryFolderStore.setRootUri(pcUri(charts))
            graph.chartImporter.import(pcUri(charts))
            // Estes testes jogam a música inteira: "Sem separar" já respondido e sem perguntar de novo (a pergunta da
            // IA tem os seus).
            graph.charts.observeCharts().first().forEach { graph.separations.decline(it.id) }
            graph.settings.update { it.copy(askSeparationAlways = false) }
        }
        DesktopLocale.applyDefault()
        composeRule.setContent {
            // A janela do teste não disputa o foco do sistema: sem isso a partida pausaria como num Alt+Tab.
            val real = LocalWindowInfo.current
            val focused = remember(real) { FocusedWindow(real) }
            CompositionLocalProvider(LocalWindowInfo provides focused) { RiffDesktopApp(graph, sendMenuKey) }
        }
        return graph
    }

    /** Da tela inicial à Biblioteca. */
    fun openLibrary() {
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(HomeTestTags.SOLO))
        composeRule.waitUntilAtLeastOneExists(hasText(TITLE), TIMEOUT_MS)
    }

    /** Biblioteca → a música → a única dificuldade → a partida. */
    fun startTheSong() {
        composeRule.clickOnWindowThread(composeRule.onNodeWithText(TITLE))
        chooseTheOnlyDifficulty()
    }

    /** Com a escolha da dificuldade aberta (pela Biblioteca ou pelo Jogar da pesquisa): a única → a partida. */
    fun chooseTheOnlyDifficulty() {
        composeRule.waitUntilAtLeastOneExists(hasTestTag(DifficultyDialogTestTags.OPTION), TIMEOUT_MS)
        composeRule.mainClock.autoAdvance = false
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(DifficultyDialogTestTags.OPTION))
        // O diálogo da dificuldade sai com animação (o relógio do teste anda à mão): enquanto ele existe, a tela tem
        // duas raízes e as teclas não sabem para qual ir.
        playInTheMatchUntil("a partida, sem o diálogo") {
            exists(STAGE) && composeRule.onAllNodes(isRoot()).fetchSemanticsNodes().size == 1
        }
    }

    /**
     * A partida pede um quadro atrás do outro, e a espera do teste pela tela parada só voltaria no fim da música:
     * com a partida aberta, o relógio do teste anda à mão, um quadro de cada vez, enquanto a música anda de verdade.
     * Uma tela de fora da partida ([screen]) só vale quando a partida já saiu de vez (a transição terminou); aí o
     * relógio volta a andar sozinho.
     */
    fun playUntil(screen: SemanticsMatcher) = play("a tela ${screen.description}") {
        exists(screen) && !exists(STAGE)
    }

    /** Dentro da partida: [what] aparece (a pausa, o próprio palco) ou a música chega a um ponto. */
    fun playInTheMatchUntil(what: String, done: () -> Boolean) = play(what, done)

    private fun play(what: String, done: () -> Boolean) {
        composeRule.mainClock.autoAdvance = false
        val deadline = System.currentTimeMillis() + MATCH_TIMEOUT_MS
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "não chegou: $what" }
            composeRule.mainClock.advanceTimeByFrame()
            Thread.sleep(FRAME_SLEEP_MS)
        }
        composeRule.mainClock.autoAdvance = !exists(STAGE)
    }

    fun exists(matcher: SemanticsMatcher) = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /** A partida anda até a música chegar a [ms] (pela saída falsa). */
    fun playTo(ms: Long) {
        val sink = synchronized(sinks) { sinks.last() }
        playInTheMatchUntil("a música em $ms ms") { sink.positionMs >= ms }
    }

    /**
     * Pelas teclas de verdade, lendo a posição da música na saída falsa: [fret] segurada antes da primeira nota e
     * [strum] na hora; sem [strum] (tocar sem palheta), a própria [fret] desce na hora.
     */
    fun playTheFirstNote(fret: Key, strum: Key? = Key.DirectionDown) {
        val sink = synchronized(sinks) { sinks.last() }
        fun playTo(ms: Long) = playInTheMatchUntil("a música em $ms ms") { sink.positionMs >= ms }
        if (strum != null) {
            playTo(FIRST_NOTE_MS - FRET_EARLY_MS)
            pressKeys { keyDown(fret) }
        }
        playTo(FIRST_NOTE_MS - STRUM_EARLY_MS)
        if (strum != null) pressKeys { pressKey(strum) } else pressKeys { keyDown(fret) }
        pressKeys { keyUp(fret) }
    }

    /** O recorde gravado da música (a única, na única dificuldade). */
    fun record(): ScoreRecord? = runBlocking {
        val chartId = graph.charts.observeCharts().first().single().id
        graph.scores.best(chartId, Difficulty.EXPERT)
    }

    fun pressKeys(block: KeyInjectionScope.() -> Unit) =
        composeRule.runOnUiThread { composeRule.onRoot().performKeyInput(block) }

    companion object {
        const val TIMEOUT_MS = 15_000L
        const val TITLE = "Tom do PC"
        const val PLAY_AGAIN = "Jogar de novo"
        const val BACK = "Voltar"
        val STAGE = hasTestTag(GameplayTestTags.STAGE)

        private const val MATCH_TIMEOUT_MS = 30_000L
        private const val SONG_MS = 2_000
        private const val FIRST_NOTE_MS = 1_000L
        private const val FRET_EARLY_MS = 300L
        private const val STRUM_EARLY_MS = 15L
        private const val FRAME_SLEEP_MS = 4L

        // 120 BPM e 192 ticks por batida: 384 = 1 s, 576 = 1,5 s.
        private val CHART = """
            [Song]
            {
              Name = "$TITLE"
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

/**
 * O FFmpeg carregado neste processo antes da primeira partida. A primeira abertura carrega as bibliotecas nativas (o
 * JavaCPP procura cada uma no classpath e no disco): ~2,5 s com a máquina livre e 15 a 20 s com ela ocupada, uma vez
 * só por processo, e caía no prazo da primeira partida (a música carregando, ainda sem o palco). Aberto aqui, fora do
 * prazo, o prazo conta só a partida.
 */
private object LoadedFfmpeg {
    private const val TONE_MS = 100

    init {
        val tone = File.createTempFile("riff-ffmpeg", ".wav")
        try {
            writeTone(tone, TONE_MS)
            FfmpegPcmSource.open(tone.path, FileRandomReadOpener()).close()
        } finally {
            tone.delete()
        }
    }

    fun ensure() = Unit
}

/** A janela do teste sempre com foco (ela não disputa o foco do sistema). */
internal class FocusedWindow(real: WindowInfo) : WindowInfo by real {
    override val isWindowFocused: Boolean get() = true
}

internal object SilentSfx : SfxPlayer {
    override fun hit(lane: Int) = Unit

    override fun miss() = Unit

    override fun setVolumes(hit: Float, miss: Float) = Unit

    override fun release() = Unit
}

/** O Encore sem internet: toda conversa falha como uma rede fora do ar. */
internal object NoInternet : HttpTransport {
    override suspend fun post(url: String, json: String): HttpResponse = throw UnknownHostException("api.enchor.us")

    override suspend fun get(url: String, range: LongRange?): HttpResponse =
        throw UnknownHostException("files.enchor.us")
}

/** Um WAV de 16 bits, mono, 48 kHz, com um lá (440 Hz) de [durationMs]. */
internal fun writeTone(file: File, durationMs: Int) {
    val rate = 48_000
    val samples = rate * durationMs / 1_000
    DataOutputStream(FileOutputStream(file).buffered()).use { out ->
        fun int(value: Int) = out.writeInt(Integer.reverseBytes(value))
        fun short(value: Int) = out.writeShort(java.lang.Short.reverseBytes(value.toShort()).toInt())
        out.writeBytes("RIFF")
        int(36 + samples * 2)
        out.writeBytes("WAVEfmt ")
        int(16)
        short(1) // PCM
        short(1) // mono
        int(rate)
        int(rate * 2)
        short(2)
        short(16)
        out.writeBytes("data")
        int(samples * 2)
        repeat(samples) { short((sin(2 * PI * 440 * it / rate) * 8_000).toInt()) }
    }
}

/**
 * Saída falsa no ritmo do relógio: aceita no máximo [BUFFER_MS] à frente do que já "tocou", e o que tocou é o tempo
 * desde [start] (sem contar as pausas). A música anda em tempo real sem alto-falante.
 */
private class RealTimeSink(private val rate: Int) : AudioSink {
    private val lock = Object()
    private var playedBefore = 0L
    private var startedAtNs = 0L
    private var written = 0L

    @Volatile private var running = false

    @Volatile private var stopped = false

    val positionMs: Long get() = playedFrames * 1_000 / rate

    override fun start() = synchronized(lock) {
        if (!running) {
            startedAtNs = System.nanoTime()
            running = true
        }
    }

    override fun pause() = synchronized(lock) {
        if (running) {
            playedBefore = clockFrames()
            running = false
        }
    }

    override fun stop() {
        stopped = true
        pause()
    }

    override fun write(buffer: FloatArray, offsetFrames: Int, frames: Int): Int {
        var done = 0
        while (done < frames && running && !stopped) {
            val room = synchronized(lock) { clockFrames() + rate * BUFFER_MS / 1_000 - written }
            if (room <= 0) {
                Thread.sleep(1)
                continue
            }
            val count = minOf(room, (frames - done).toLong()).toInt()
            synchronized(lock) { written += count }
            done += count
        }
        return done
    }

    override val playedFrames: Long get() = synchronized(lock) { minOf(written, clockFrames()) }

    override fun release() = Unit

    private fun clockFrames(): Long =
        if (running) playedBefore + (System.nanoTime() - startedAtNs) * rate / 1_000_000_000 else playedBefore

    private companion object {
        const val BUFFER_MS = 50
    }
}
