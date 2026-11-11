package com.rodrigomachado.riff.feature.room

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.rodrigomachado.riff.core.model.ChartArtUnavailableException
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.room.Room
import com.rodrigomachado.riff.core.room.RoomState
import com.rodrigomachado.riff.core.room.SongStatus
import com.rodrigomachado.riff.core.ui.art.ChartArtImages
import com.rodrigomachado.riff.core.ui.art.ChartThumbImages
import com.rodrigomachado.riff.core.ui.component.ChartThumbTestTags
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class RoomScreensTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val events = mutableListOf<String>()

    @Test
    fun entryCreatesOrGoesToTheCode() {
        composeRule.setContent {
            RiffTheme {
                RoomEntryContent(RoomEntryUiState(), onCreate = { events += "create" }, onJoin = { events += "join" })
            }
        }

        composeRule.onNodeWithText("Criar sala").performClick()
        composeRule.onNodeWithText("Entrar com código").performClick()

        assertEquals(listOf("create", "join"), events)
    }

    @Test
    fun entryWithoutInternetSaysSo() {
        composeRule.setContent {
            RiffTheme {
                RoomEntryContent(
                    RoomEntryUiState(error = RoomError.NO_CONNECTION),
                    onCreate = {},
                    onJoin = {},
                )
            }
        }

        composeRule.onNodeWithText("Sem conexão. Tente de novo.").assertExists()
    }

    /** O campo guarda o que o ViewModel normalizou; Entrar só com as 4 letras. */
    @Test
    fun joinTypesTheCodeAndEnters() {
        var state by mutableStateOf(RoomJoinUiState())
        composeRule.setContent {
            RiffTheme {
                RoomJoinContent(
                    state,
                    onCode = { state = state.copy(code = it.uppercase().take(4)) },
                    onJoin = { events += "join:${state.code}" },
                )
            }
        }

        composeRule.onNodeWithText("Código da sala").assertExists()
        composeRule.onNodeWithText("Entrar").assertIsNotEnabled()
        composeRule.onNodeWithTag(RoomTestTags.CODE).performTextInput("kqzx")
        composeRule.onNodeWithText("Entrar").assertIsEnabled().performClick()

        assertEquals(listOf("join:KQZX"), events)
    }

    @Test
    fun joinErrorsAreClear() {
        var state by mutableStateOf(RoomJoinUiState(code = "KQZX", error = RoomError.NOT_FOUND))
        composeRule.setContent { RiffTheme { RoomJoinContent(state, onCode = {}, onJoin = {}) } }

        composeRule.onNodeWithText("Sala não encontrada.").assertExists()
        state = state.copy(error = RoomError.FULL)
        composeRule.onNodeWithText("Sala cheia (4 jogadores).").assertExists()
        state = state.copy(error = RoomError.NO_CONNECTION)
        composeRule.onNodeWithText("Sem conexão. Tente de novo.").assertExists()
    }

    @Test
    fun lobbyShowsTheCodeThePlayersAndLeave() {
        val room = sampleRoom("Ana", "Bia", me = "Bia", gone = setOf("Bia"), pcs = setOf("Bia"))
        composeRule.setContent {
            RiffTheme { RoomLobbyContent(RoomLobbyUiState.Ready(room), "KQZX", onLeave = { events += "leave" }) }
        }

        composeRule.onNodeWithText("KQZX").assertExists()
        composeRule.onNodeWithText("Ana").assertExists()
        composeRule.onNodeWithText("Dono").assertExists()
        composeRule.onNodeWithText("Celular").assertExists()
        composeRule.onNodeWithText("PC").assertExists()
        composeRule.onNodeWithText("sumiu").assertExists()
        composeRule.onNodeWithText("Aguardando jogadores…").assertExists()
        composeRule.onNodeWithText("Sair da sala").performClick()

        assertEquals(listOf("leave"), events)
    }

    @Test
    fun aFullRoomStopsWaiting() {
        val room = sampleRoom("Ana", "Bia", "Caio", "Duda")
        composeRule.setContent { RiffTheme { RoomLobbyContent(RoomLobbyUiState.Ready(room), "KQZX", onLeave = {}) } }

        composeRule.onNodeWithText("Aguardando jogadores…").assertDoesNotExist()
    }

    @Test
    fun anEndedRoomGoesBack() {
        composeRule.setContent {
            RiffTheme { RoomLobbyContent(RoomLobbyUiState.Ended, "KQZX", onLeave = { events += "back" }) }
        }

        composeRule.onNodeWithText("A sala acabou.").assertExists()
        composeRule.onNodeWithText("Voltar").performClick()

        assertEquals(listOf("back"), events)
    }

    // ---- M2 ----

    private val actions = RoomLobbyActions(
        onChooseSong = { events += "choose" },
        onDifficulty = { events += "difficulty:$it" },
        onToggleReady = { events += "ready" },
        onDownload = { events += "download" },
        onStart = { events += "start" },
    )

    /** O botão Pronto (o carimbo "Pronto" dos cartões não é clicável). */
    private val readyButton = hasText("Pronto") and hasClickAction()

    private fun showLobby(state: RoomLobbyUiState.Ready) = composeRule.setContent {
        RiffTheme { RoomLobbyContent(state, "KQZX", onLeave = {}, actions = actions) }
    }

    @Test
    fun ownerWithoutASongChoosesOne() {
        showLobby(RoomLobbyUiState.Ready(sampleRoom("Ana", "Bia")))

        composeRule.onNodeWithText("Nenhuma música escolhida").assertExists()
        composeRule.onNodeWithText("Escolher música").performScrollTo().performClick()
        composeRule.onNodeWithText("Começar").assertIsNotEnabled()
        composeRule.onNodeWithText("Precisa de pelo menos 2 prontos").assertExists()

        assertEquals(listOf("choose"), events)
    }

    /** O dono com a música e sem o Pronto: o Começar desligado e o aviso para ficar pronto. */
    @Test
    fun ownerNotReadyIsAskedToGetReady() {
        val room = sampleRoom("Ana", "Bia", "Caio").withSong()
            .withPlayer("Ana")
            .withPlayer("Bia", ready = true)
            .withPlayer("Caio", ready = true)
        showLobby(RoomLobbyUiState.Ready(room, mySong = MySong.Has))

        composeRule.onNodeWithText("Começar").assertIsNotEnabled()
        // O aviso vem na linha do que falta ("2 prontos · Fique pronto para começar").
        composeRule.onNodeWithText("Fique pronto para começar", substring = true).assertExists()
        composeRule.onNodeWithText("Precisa de pelo menos 2 prontos", substring = true).assertDoesNotExist()
    }

    @Test
    fun aRefusedChoiceTellsTheOwner() {
        showLobby(RoomLobbyUiState.Ready(sampleRoom("Ana", "Bia"), chooseFailed = true))

        composeRule.onNodeWithText("Não deu para escolher a música. Tente de novo.").assertExists()
    }

    @Test
    fun guestWithoutASongJustWaits() {
        showLobby(RoomLobbyUiState.Ready(sampleRoom("Ana", "Bia", me = "Bia")))

        composeRule.onNodeWithText("Nenhuma música escolhida").assertExists()
        composeRule.onNodeWithText("Escolher música").assertDoesNotExist()
        composeRule.onNodeWithText("Começar").assertDoesNotExist()
    }

    /** A dificuldade só entre as da música; Pronto e o carimbo de cada um. */
    @Test
    fun withTheSongChoosesTheDifficultyAndReadies() {
        val room = sampleRoom("Ana", "Bia", "Caio", me = "Bia").withSong()
            .withPlayer("Ana", ready = true)
            .withPlayer("Bia", difficulty = null)
            .withPlayer("Caio", song = SongStatus.MISSING, difficulty = null)
        showLobby(RoomLobbyUiState.Ready(room, mySong = MySong.Has))

        composeRule.onNodeWithText("Through the Fire").assertExists()
        composeRule.onNodeWithText("Trocar").assertDoesNotExist() // só o dono troca
        composeRule.onNodeWithText("Médio").assertDoesNotExist()
        composeRule.onNode(readyButton).assertIsNotEnabled() // sem dificuldade ainda
        composeRule.onNodeWithText("Expert").performScrollTo().performClick()
        composeRule.onNode(hasText("Pronto") and !hasClickAction()).assertExists() // o carimbo da Ana
        composeRule.onNodeWithText("Escolhendo").assertExists()
        composeRule.onNodeWithText("Sem a música").assertExists()

        assertEquals(listOf("difficulty:${Difficulty.EXPERT}"), events)
    }

    @Test
    fun readyTogglesAndTheOwnerStarts() {
        val room = sampleRoom("Ana", "Bia").withSong().withPlayer("Ana", ready = true).withPlayer("Bia", ready = true)
        showLobby(RoomLobbyUiState.Ready(room, mySong = MySong.Has, canStart = true))

        composeRule.onNodeWithText("Trocar").assertExists()
        // Pronto e Começar ficam fixos embaixo, fora da rolagem.
        composeRule.onNodeWithText("Não estou pronto").performClick()
        composeRule.onNodeWithText("Começar").assertIsEnabled().performClick()

        assertEquals(listOf("ready", "start"), events)
    }

    @Test
    fun after30sStartsWithWhoIsReady() {
        val room = sampleRoom("Ana", "Bia", "Caio").withSong()
            .withPlayer("Ana", ready = true).withPlayer("Bia", ready = true).withPlayer("Caio")
        showLobby(RoomLobbyUiState.Ready(room, mySong = MySong.Has, canStart = true))

        // O aviso vem na linha do que falta; o botão continua "Começar".
        composeRule.onNodeWithText("Começar com quem está pronto", substring = true).assertExists()
        composeRule.onNodeWithText("Começar").assertIsEnabled()
    }

    @Test
    fun missingEncoreSongOffersTheDownload() {
        val room = sampleRoom("Ana", "Bia", me = "Bia").withSong().withPlayer("Bia", song = SongStatus.MISSING)
        showLobby(RoomLobbyUiState.Ready(room, mySong = MySong.Missing(canDownload = true)))

        composeRule.onNodeWithText("Você não tem esta música.").assertExists()
        composeRule.onNodeWithText("Baixar do Encore").performScrollTo().performClick()
        composeRule.onNode(readyButton).assertDoesNotExist()

        assertEquals(listOf("download"), events)
    }

    @Test
    fun missingLocalSongHasNoDownload() {
        val room = sampleRoom("Ana", "Bia", me = "Bia").withSong(SONG_B).withPlayer("Bia", song = SongStatus.MISSING)
        showLobby(RoomLobbyUiState.Ready(room, mySong = MySong.Missing(canDownload = false)))

        composeRule.onNodeWithText("Você não tem esta música.").assertExists()
        composeRule.onNodeWithText("Baixar do Encore").assertDoesNotExist()
    }

    @Test
    fun downloadingShowsProgress() {
        val room = sampleRoom("Ana", "Bia", me = "Bia").withSong().withPlayer("Bia", song = SongStatus.DOWNLOADING)
        showLobby(RoomLobbyUiState.Ready(room, mySong = MySong.Downloading(0.4f)))

        composeRule.onAllNodesWithText("Baixando…").assertCountEquals(2) // o meu aviso e o meu carimbo
    }

    @Test
    fun theCountdownSaysStarting() {
        val room: Room = sampleRoom("Ana", "Bia").withSong().copy(state = RoomState.COUNTDOWN)
        showLobby(RoomLobbyUiState.Ready(room, mySong = MySong.Has))

        composeRule.onNodeWithText("Começando…").assertExists()
        composeRule.onNodeWithText("Trocar").assertIsNotEnabled()
    }

    // ---- Design novo ----

    /** Compartilhar abre o "Compartilhar" do sistema com o convite e o código. */
    @Test
    fun shareSendsTheCodeText() {
        lateinit var context: Context
        composeRule.setContent {
            context = LocalContext.current
            RiffTheme { RoomLobbyContent(RoomLobbyUiState.Ready(sampleRoom("Ana", "Bia")), "KQZX", onLeave = {}) }
        }

        composeRule.onNodeWithContentDescription("Compartilhar o código").performClick()
        composeRule.waitForIdle()

        val chooser = shadowOf(context as Activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION") // o getParcelableExtra com a classe só existe a partir do Android 13
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("Entra na minha sala do Riff: KQZX", send.getStringExtra(Intent.EXTRA_TEXT))
        composeRule.onNodeWithText("Código copiado").assertDoesNotExist() // no celular, nada é copiado
    }

    /** A linha do que falta: quantos prontos e por quem a sala espera (baixando, ainda não pronto, a música). */
    @Test
    fun whatIsMissingLine() {
        var state by mutableStateOf(
            RoomLobbyUiState.Ready(
                sampleRoom("Rodrigo", "Ana", "Bruno_88").withSong()
                    .withPlayer("Rodrigo", ready = true)
                    .withPlayer("Ana", ready = true)
                    .withPlayer("Bruno_88", song = SongStatus.DOWNLOADING, difficulty = null),
                mySong = MySong.Has,
                canStart = true,
            ),
        )
        composeRule.setContent { RiffTheme { RoomLobbyContent(state, "KQZX", onLeave = {}, actions = actions) } }

        composeRule.onNodeWithText("2 prontos · esperando Bruno_88 terminar de baixar").assertExists()

        state = RoomLobbyUiState.Ready(
            sampleRoom("Rodrigo", "Ana", "Bruno_88").withSong()
                .withPlayer("Rodrigo", ready = true)
                .withPlayer("Ana")
                .withPlayer("Bruno_88"),
            mySong = MySong.Has,
        )
        val line = "1 pronto · esperando Ana e Bruno_88 ficarem prontos · Precisa de pelo menos 2 prontos"
        composeRule.onNodeWithText(line).assertExists()

        state = RoomLobbyUiState.Ready(sampleRoom("Ana", "Rodrigo", me = "Rodrigo"))
        composeRule.onNodeWithText("Esperando Ana escolher a música").assertExists()
    }

    /** O topo: "Sair da sala", o código pequeno e o compartilhar; logo abaixo, os jogadores contados. */
    @Test
    fun topShowsTheSmallCodeAndThePlayerCount() {
        showLobby(RoomLobbyUiState.Ready(sampleRoom("Ana", "Bia", "Caio")))

        composeRule.onNodeWithText("SALA").assertExists()
        composeRule.onNodeWithText("KQZX").assertExists()
        composeRule.onNodeWithText("Jogadores · 3 de 4").assertExists()
        composeRule.onNodeWithText("Código da sala").assertDoesNotExist() // o código grande saiu
    }

    /** Pronto e Começar lado a lado, Pronto à esquerda. */
    @Test
    fun readyAndStartSideBySide() {
        val room = sampleRoom("Ana", "Bia").withSong().withPlayer("Ana").withPlayer("Bia", ready = true)
        showLobby(RoomLobbyUiState.Ready(room, mySong = MySong.Has))

        val ready = composeRule.onNode(readyButton).fetchSemanticsNode().boundsInRoot
        val start = composeRule.onNodeWithText("Começar").fetchSemanticsNode().boundsInRoot
        assertTrue("na mesma linha: $ready / $start", ready.bottom > start.top && start.bottom > ready.top)
        assertTrue("Pronto à esquerda: $ready / $start", ready.right <= start.left)
        composeRule.onNodeWithText("Sua dificuldade").assertExists()
    }

    /**
     * O convidado sem a música: a capa é pedida antes do download (não dá para procurar: o mapa não está no banco) e,
     * quando a música chega, é pedida de novo e aparece.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // a imagem de verdade (o padrão do Robolectric não cria o bitmap)
    fun theCoverAppearsWhenTheSongIsDownloadedAfterTheFirstAsk() {
        val image = ImageBitmap(8, 8)
        var downloaded = false
        val images = ChartThumbImages(
            ChartArtImages { if (downloaded) image else throw ChartArtUnavailableException("ainda não baixou") },
        )
        val room = sampleRoom("Ana", "Bia", me = "Bia").withSong()
        var state by mutableStateOf(RoomLobbyUiState.Ready(room, mySong = MySong.Downloading(0.5f)))
        composeRule.setContent {
            RiffTheme { RoomLobbyContent(state, "KQZX", onLeave = {}, actions = actions, images = images) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ChartThumbTestTags.ART).assertDoesNotExist()

        downloaded = true
        state = state.copy(mySong = MySong.Has)

        composeRule.waitUntil(WAIT_MS) {
            composeRule.onAllNodesWithTag(ChartThumbTestTags.ART).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
