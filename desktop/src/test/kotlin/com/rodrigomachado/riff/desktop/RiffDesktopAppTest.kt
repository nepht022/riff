package com.rodrigomachado.riff.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.rodrigomachado.riff.core.data.folder.pcUri
import com.rodrigomachado.riff.core.data.ranking.SubmitStatus
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.ScoreRecord
import com.rodrigomachado.riff.core.room.Room
import com.rodrigomachado.riff.core.room.RoomDevice
import com.rodrigomachado.riff.core.room.RoomPlayer
import com.rodrigomachado.riff.core.room.RoomSong
import com.rodrigomachado.riff.core.room.SongStatus
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.input.LocalGamepad
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.desktop.pad.NoPadSource
import com.rodrigomachado.riff.feature.home.HomeTestTags
import com.rodrigomachado.riff.feature.results.ResultsContent
import com.rodrigomachado.riff.feature.results.ResultsUiState
import com.rodrigomachado.riff.feature.room.MySong
import com.rodrigomachado.riff.feature.room.RoomLobbyContent
import com.rodrigomachado.riff.feature.room.RoomLobbyUiState
import io.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Locale

/**
 * O Skia do PC suaviza o contorno das letras de um jeito no Windows e de outro no Linux do CI (0,39% dos pixels da
 * tela inicial, todos nos textos, medido em 2026-10-31). Até 1% passa; layout e cores continuam conferidos, e os
 * textos são conferidos pelos testes de conteúdo. As screenshots do celular seguem exatas.
 */
private val TextRasterTolerance =
    RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))

@OptIn(ExperimentalTestApi::class)
class RiffDesktopAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val system = Locale.getDefault()
    private val graph by lazy {
        DesktopGraph(
            temp.newFolder("dados"),
            padSource = NoPadSource,
            // Sem o ranking (desligado, como sem ranking.url): nunca o script real.
            rankingUrl = "",
            rankingTransport = OfflineRankingScript,
            // Sem a Sala (desligada, "em breve", como sem o Firebase no local.properties): a imagem não muda com ele.
            roomApiKey = "",
            roomDbUrl = "",
        )
    }

    @After
    fun restore() {
        Locale.setDefault(system)
        graph.close()
    }

    @Test
    fun opensOnTheHomeScreenInPortugueseEvenOnAnEnglishWindows() {
        Locale.setDefault(Locale.US)
        DesktopLocale.applyDefault()
        composeRule.setContent { RiffDesktopApp(graph) }

        composeRule.onNodeWithText("Sozinho").assertExists()
        composeRule.onNodeWithContentDescription("Sala, em breve").assertExists()
    }

    @Test
    fun aSmallWindowStillReachesTheRanking() {
        DesktopLocale.applyDefault()
        composeRule.setContent { Box(Modifier.size(800.dp, 500.dp)) { RiffDesktopApp(graph) } }

        composeRule.onNodeWithTag(HomeTestTags.RANKING).performScrollTo().assertIsDisplayed()
    }

    /**
     * A Biblioteca do PC na janela padrão: o topo (Voltar · ⋮), "+ Mapas", o filtro, os chips, os cartões (sem
     * imagem: as iniciais; um com recorde) e a barra de teclas no rodapé (sem controle: só o teclado).
     */
    @Test
    fun libraryScreenshot() {
        val folder = temp.newFolder("mapas")
        listOf("Through the Fire and Flames" to "DragonForce", "Bohemian Rhapsody" to "Queen").forEach { (title, by) ->
            File(folder, title).apply { mkdirs() }.run {
                File(this, "notes.chart").writeText(chart(title, by))
                File(this, "song.ogg").writeBytes(ByteArray(10))
            }
        }
        runBlocking {
            graph.libraryFolderStore.setRootUri(pcUri(folder))
            graph.chartImporter.import(pcUri(folder))
            val fire = graph.charts.observeCharts().first().first { it.artist == "DragonForce" }
            graph.scores.save(ScoreRecord(0, fire.id, Difficulty.EXPERT, 412_380, 0.97f, 300, 900, 40, 6, 1_000))
        }
        DesktopLocale.applyDefault()
        composeRule.setContent { RiffDesktopApp(graph) }
        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(HomeTestTags.SOLO))
        composeRule.waitUntilAtLeastOneExists(hasText("Recorde 412.380 · Expert"), TIMEOUT_MS)
        // O clique do teste deixa o ponteiro no meio da janela, em cima de um cartão: tira-o de lá.
        composeRule.runOnUiThread { composeRule.onRoot().performMouseInput { moveTo(Offset(1f, 1f)) } }
        composeRule.waitForIdle()

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/Library_desktop.png", roborazziOptions = TextRasterTolerance)
    }

    /** Na janela padrão dos testes do Compose para desktop (1024×768). */
    @Test
    fun homeScreenshot() {
        DesktopLocale.applyDefault()
        composeRule.setContent { RiffDesktopApp(graph) }

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/Home_desktop.png", roborazziOptions = TextRasterTolerance)
    }

    /**
     * O Resultado do PC na janela padrão (1024×768): em duas colunas (capa, título, pontos e o carimbo da nota; a
     * precisão, os blocos, o ranking e os botões lado a lado) e a barra de teclas no rodapé (sem controle: só o
     * teclado). O conteúdo direto, como o app o mostra (o fundo, o tema, o controle do PC): chegar lá jogando daria
     * números diferentes a cada vez.
     */
    @Test
    fun resultsScreenshot() {
        DesktopLocale.applyDefault()
        composeRule.setContent {
            RiffTheme {
                CompositionLocalProvider(LocalGamepad provides graph.gamepad) {
                    StageBackground(Modifier.fillMaxSize()) {
                        ResultsContent(
                            ResultsUiState.Ready(
                                ScoreRecord(7, "fogo", Difficulty.EXPERT, 412_380, 0.93f, 300, 900, 40, 6, 1_000),
                                "Through the Fire and Flames",
                                "DragonForce",
                                isNewBest = true,
                                ranking = SubmitStatus.Placed(3, 27),
                                rankingOn = true,
                                previousBest = 398_120,
                            ),
                            onPlayAgain = { _, _ -> },
                            onDone = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("antes: 398.120").assertExists()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/Results_desktop.png", roborazziOptions = TextRasterTolerance)
    }

    /**
     * A sala do PC na janela padrão (1024×768): a coluna no meio, o topo com "Sair da sala", o código pequeno e o
     * copiar; os jogadores, a música com a capa (as iniciais: o mapa não existe aqui), a dificuldade, a linha do que
     * falta, Pronto e Começar lado a lado e a barra de teclas no rodapé. O conteúdo direto, como o app o mostra.
     */
    @Test
    fun roomScreenshot() {
        DesktopLocale.applyDefault()
        val players = listOf("Rodrigo", "Ana", "Bruno_88").mapIndexed { i, name ->
            RoomPlayer(
                uid = name,
                name = name,
                device = if (i == 0) RoomDevice.PC else RoomDevice.PHONE,
                joinedAt = i.toLong(),
                seenAt = i.toLong(),
                gone = false,
                difficulty = Difficulty.EXPERT.takeIf { i < 2 },
                ready = i < 2,
                song = if (i < 2) SongStatus.HAS else SongStatus.DOWNLOADING,
            )
        }
        val song = RoomSong(
            chartId = "fogo",
            title = "Through the Fire and Flames",
            artist = "DragonForce",
            difficulties = Difficulty.entries.toSet(),
            durationMs = null,
            encore = null,
        )
        val room = Room("KQZX", owner = "Rodrigo", players = players, me = "Rodrigo", song = song)
        composeRule.setContent {
            RiffTheme {
                CompositionLocalProvider(LocalGamepad provides graph.gamepad) {
                    StageBackground(Modifier.fillMaxSize()) {
                        RoomLobbyContent(
                            RoomLobbyUiState.Ready(room, mySong = MySong.Has, canStart = true),
                            "KQZX",
                            onLeave = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("2 prontos · esperando Bruno_88 terminar de baixar").assertExists()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/Room_desktop.png", roborazziOptions = TextRasterTolerance)
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L

        fun chart(title: String, artist: String) = """
            [Song]
            {
              Name = "$title"
              Artist = "$artist"
            }
            [ExpertSingle]
            {
              192 = N 0 0
            }
        """.trimIndent()
    }
}
