package com.rodrigomachado.riff.feature.room

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.room.HttpReply
import com.rodrigomachado.riff.core.room.RoomState
import com.rodrigomachado.riff.core.room.SongStatus
import com.rodrigomachado.riff.core.room.testing.FakeFirebase
import com.rodrigomachado.riff.core.room.testing.FakePlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A sala ao vivo com o Firebase falso: os jogadores, o fim da sala, o batimento e o Sair; a música, a dificuldade, o
 * Pronto, o download do Encore e o Começar (M2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomLobbyViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var now = 1_000_000L
    private val fake = FakeFirebase(clock = { now }, uid = "u1")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fake.seedRoom("ABCD", owner = "u1", players = listOf(FakePlayer("u1", "Ana")))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** A tela aberta: alguém olhando o estado (o batimento e o streaming só rodam assim). */
    private fun TestScope.opened(
        uid: String = "u1",
        library: FakeRoomLibrary = FakeRoomLibrary(),
    ): RoomLobbyViewModel {
        val vm = RoomLobbyViewModel("ABCD", fake.repository(uid), library.check())
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        return vm
    }

    private fun RoomLobbyViewModel.names() = (uiState.value as RoomLobbyUiState.Ready).room.players.map { it.name }

    private fun beats() = fake.writes.count { it.uid == "u1" && it.method == "PATCH" && "vistoEm" in it.body.orEmpty() }

    @Test
    fun lobbyShowsPlayersLive() = runTest(dispatcher) {
        val vm = opened()
        assertEquals(listOf("Ana"), vm.names())
        assertTrue((vm.uiState.value as RoomLobbyUiState.Ready).room.iAmOwner)

        fake.repository("u2").join("ABCD", "Bia")
        runCurrent()

        assertEquals(listOf("Ana", "Bia"), vm.names())
    }

    @Test
    fun lobbyEndsWhenTheRoomIsDeleted() = runTest(dispatcher) {
        val vm = opened()

        fake.set("salas/ABCD", "null")
        runCurrent()

        assertEquals(RoomLobbyUiState.Ended, vm.uiState.value)
    }

    /** O dono me tirou (sumido há mais de 5 min): a sala acabou para mim. */
    @Test
    fun lobbyEndsWhenIAmRemoved() = runTest(dispatcher) {
        fake.seedRoom("ABCD", owner = "u2", players = listOf(FakePlayer("u2", "Bia"), FakePlayer("u1", "Ana")))
        val vm = opened()
        assertEquals(listOf("Ana", "Bia"), vm.names()) // mesmo entrouEm: o uid desempata

        fake.set("salas/ABCD/jogadores/u1", "null")
        runCurrent()

        assertEquals(RoomLobbyUiState.Ended, vm.uiState.value)
    }

    /** Sair não mostra "a sala acabou" no caminho (sair me tira da sala, ou apaga a sala). */
    @Test
    fun leavingDoesNotFlashEnded() = runTest(dispatcher) {
        val vm = opened()
        val states = mutableListOf<RoomLobbyUiState>()
        backgroundScope.launch { vm.uiState.collect { states += it } }
        runCurrent()

        vm.leave()
        runCurrent()

        assertTrue(vm.left.value)
        assertFalse(RoomLobbyUiState.Ended in states)
    }

    @Test
    fun leaveNavigatesBack() = runTest(dispatcher) {
        val vm = opened()
        assertFalse(vm.left.value)

        vm.leave()
        runCurrent()

        assertTrue(vm.left.value)
        assertNull(fake.get("salas/ABCD")) // o último a sair apaga a sala
    }

    /** Batimento ao abrir e a cada 20 s enquanto a tela está aberta. */
    @Test
    fun heartbeatEvery20Seconds() = runTest(dispatcher) {
        opened()
        assertEquals(1, beats())

        advanceTimeBy(19_000)
        assertEquals(1, beats())
        advanceTimeBy(1_001)
        assertEquals(2, beats())
        advanceTimeBy(20_000)
        assertEquals(3, beats())
    }

    /** Sem internet ao abrir: avisa e tenta de novo sozinho. */
    @Test
    fun withoutInternetShowsNoConnectionAndRetries() = runTest(dispatcher) {
        fake.online = false
        val vm = opened()

        assertEquals(RoomLobbyUiState.NoConnection, vm.uiState.value)

        fake.online = true
        advanceTimeBy(5_001)

        assertEquals(listOf("Ana"), vm.names())
    }

    /** O login recusado ao abrir (Google fora do ar, resposta sem os campos): sem conexão, sem fechar o app. */
    @Test
    fun refusedLoginShowsNoConnectionAndRetries() = runTest(dispatcher) {
        fake.loginReply = HttpReply(503, "", emptyMap())
        val vm = opened()
        assertEquals(RoomLobbyUiState.NoConnection, vm.uiState.value)

        fake.loginReply = HttpReply(200, "{}", emptyMap())
        advanceTimeBy(5_001)
        assertEquals(RoomLobbyUiState.NoConnection, vm.uiState.value)

        fake.loginReply = null
        advanceTimeBy(5_001)
        assertEquals(listOf("Ana"), vm.names())
    }

    // ---- M2: a música, a dificuldade, o Pronto e o Começar ----

    private fun RoomLobbyViewModel.ready() = uiState.value as RoomLobbyUiState.Ready

    private fun player(uid: String, field: String) = fake.get("salas/ABCD/jogadores/$uid/$field")

    private fun seedWithSong(
        owner: String,
        vararg players: FakePlayer,
        song: com.rodrigomachado.riff.core.room.RoomSong = SONG_A,
    ) = fake.seedRoom("ABCD", owner = owner, players = players.toList(), songJson = songJson(song))

    private fun ready(uid: String, song: SongStatus = SongStatus.HAS, ready: Boolean = true) =
        FakePlayer(uid, uid, difficulty = Difficulty.EXPERT, ready = ready, song = song)

    /** A Biblioteca no modo de escolha devolve o mapa e a dificuldade; a sala mostra a música escolhida. */
    @Test
    fun ownerPicksASongFromTheLibrary() = runTest(dispatcher) {
        val vm = opened(library = FakeRoomLibrary(CHART_A))
        assertNull(vm.ready().room.song)

        vm.chooseSong(CHART_A, Difficulty.EXPERT)
        runCurrent()

        val state = vm.ready()
        assertEquals("Through the Fire", state.room.song?.title)
        assertEquals(MD5_A, state.room.song?.encore?.md5)
        assertEquals(MySong.Has, state.mySong)
        assertEquals(Difficulty.EXPERT, state.me?.difficulty)
        assertEquals("tem", player("u1", "musica"))
        assertEquals(false, player("u1", "pronto"))
    }

    @Test
    fun guestSeesTheSongAndChoosesADifficultyOfTheSong() = runTest(dispatcher) {
        seedWithSong("u2", FakePlayer("u2"), FakePlayer("u1"))
        val vm = opened(library = FakeRoomLibrary(CHART_A))
        assertEquals(SONG_A.title, vm.ready().room.song?.title)
        assertEquals(MySong.Has, vm.ready().mySong)
        assertEquals("tem", player("u1", "musica"))

        vm.chooseDifficulty(Difficulty.EXPERT)
        runCurrent()
        vm.chooseDifficulty(Difficulty.MEDIUM) // a música não tem Médio
        runCurrent()

        assertEquals("EXPERT", player("u1", "dificuldade"))
        vm.toggleReady()
        runCurrent()
        assertEquals(true, player("u1", "pronto"))
        vm.toggleReady()
        runCurrent()
        assertEquals(false, player("u1", "pronto"))
    }

    /** Pronto só com a dificuldade escolhida. */
    @Test
    fun readyNeedsADifficulty() = runTest(dispatcher) {
        seedWithSong("u2", FakePlayer("u2"), FakePlayer("u1"))
        val vm = opened(library = FakeRoomLibrary(CHART_A))

        vm.toggleReady()
        runCurrent()

        assertEquals(false, player("u1", "pronto"))
    }

    @Test
    fun changingTheSongClearsReady() = runTest(dispatcher) {
        seedWithSong("u2", ready("u2"), ready("u1"))
        val vm = opened(library = FakeRoomLibrary(CHART_A, CHART_B))
        assertEquals(true, player("u1", "pronto")) // a primeira conferência mantém o Pronto desta música

        fake.repository("u2").chooseSong("ABCD", SONG_B, Difficulty.EASY)
        runCurrent()

        assertEquals(false, player("u1", "pronto"))
        assertEquals("tem", player("u1", "musica"))
        assertEquals("EXPERT", player("u1", "dificuldade")) // a música nova também tem Expert
        assertEquals(SONG_B.title, vm.ready().room.song?.title)
    }

    @Test
    fun missingEncoreSongOffersDownloadAndThenHas() = runTest(dispatcher) {
        seedWithSong("u2", ready("u2"), FakePlayer("u1"))
        val library = FakeRoomLibrary().apply { gate = CompletableDeferred() }
        val vm = opened(library = library)
        assertEquals(MySong.Missing(canDownload = true), vm.ready().mySong)
        assertEquals("sem", player("u1", "musica"))

        vm.download()
        runCurrent()

        assertEquals(MySong.Downloading(0.5f), vm.ready().mySong)
        assertEquals("baixando", player("u1", "musica"))
        val asked = library.downloads.single()
        assertEquals(MD5_A, asked.md5)
        assertEquals(SONG_A.title, asked.name)
        assertEquals(setOf(Difficulty.EASY, Difficulty.EXPERT), asked.guitarDifficulties)

        library.gate?.complete(Unit)
        runCurrent()

        assertEquals(MySong.Has, vm.ready().mySong)
        assertEquals("tem", player("u1", "musica"))
    }

    /** O pacote baixado não deu o mesmo mapa (ou o download falhou): sem a música, e pode tentar de novo. */
    @Test
    fun aDownloadThatDoesNotMatchStaysMissing() = runTest(dispatcher) {
        seedWithSong("u2", ready("u2"), FakePlayer("u1"))
        val library = FakeRoomLibrary().apply { gives = null }
        val vm = opened(library = library)

        vm.download()
        runCurrent()

        assertEquals(MySong.Missing(canDownload = true, failed = true), vm.ready().mySong)
        assertEquals("sem", player("u1", "musica"))

        library.gives = CHART_A
        library.failure = com.rodrigomachado.riff.core.data.download.ChartDownloadException.Reason.NETWORK
        vm.download()
        runCurrent()
        assertEquals(MySong.Missing(canDownload = true, failed = true), vm.ready().mySong)
    }

    @Test
    fun missingLocalSongSaysYouDoNotHaveIt() = runTest(dispatcher) {
        seedWithSong("u2", ready("u2"), FakePlayer("u1"), song = SONG_B)
        val library = FakeRoomLibrary()
        val vm = opened(library = library)

        assertEquals(MySong.Missing(canDownload = false), vm.ready().mySong)
        assertEquals("sem", player("u1", "musica"))
        vm.download()
        runCurrent()
        assertTrue(library.downloads.isEmpty())
    }

    @Test
    fun startIsOffWithOneReady() = runTest(dispatcher) {
        seedWithSong("u1", ready("u1"), ready("u2", ready = false))
        val vm = opened(library = FakeRoomLibrary(CHART_A))
        assertEquals(1, vm.ready().readyCount)
        assertFalse(vm.ready().canStart)

        vm.start()
        runCurrent()

        assertEquals("sala", fake.get("salas/ABCD/estado"))
    }

    @Test
    fun startIsOnWithTwoReady() = runTest(dispatcher) {
        seedWithSong("u1", ready("u1"), ready("u2"), ready("u3", song = SongStatus.MISSING, ready = false))
        val vm = opened(library = FakeRoomLibrary(CHART_A))
        assertTrue(vm.ready().canStart)
        assertFalse(vm.ready().withWhoIsReady)

        vm.start()
        runCurrent()

        assertEquals("contagem", fake.get("salas/ABCD/estado"))
        assertEquals(1, fake.get("salas/ABCD/rodada"))
        assertTrue(vm.ready().starting)
    }

    @Test
    fun startWithWhoIsReadyAfter30s() = runTest(dispatcher) {
        seedWithSong("u1", ready("u1"), ready("u2"), ready("u3", ready = false))
        val vm = opened(library = FakeRoomLibrary(CHART_A))
        assertFalse(vm.ready().canStart)

        now += 29_000
        advanceTimeBy(1_001)
        assertFalse(vm.ready().canStart)
        now += 1_000
        advanceTimeBy(1_001)

        assertTrue(vm.ready().canStart)
        assertTrue(vm.ready().withWhoIsReady)
    }

    /** O dono que não está pronto não começa (nem depois dos 30 s): o aviso pede que ele fique pronto. */
    @Test
    fun startIsOffWhileTheOwnerIsNotReady() = runTest(dispatcher) {
        seedWithSong("u1", ready("u1", ready = false), ready("u2"), ready("u3"))
        val vm = opened(library = FakeRoomLibrary(CHART_A))
        assertFalse(vm.ready().canStart)
        assertEquals(StartHint.BE_READY, vm.ready().startHint)

        now += 30_000
        advanceTimeBy(1_001)
        assertFalse(vm.ready().canStart)
        assertEquals(StartHint.BE_READY, vm.ready().startHint)

        vm.toggleReady()
        runCurrent()
        assertTrue(vm.ready().canStart)
        assertNull(vm.ready().startHint)
    }

    @Test
    fun startHintAsksForTwoReadyWhenTheOwnerIsTheOnlyOne() = runTest(dispatcher) {
        seedWithSong("u1", ready("u1"), ready("u2", ready = false))
        val vm = opened(library = FakeRoomLibrary(CHART_A))

        assertEquals(StartHint.NEED_TWO, vm.ready().startHint)
    }

    @Test
    fun countdownOpensTheMatchForReadyPlayersOnly() = runTest(dispatcher) {
        seedWithSong("u2", ready("u2"), ready("u1"), FakePlayer("u3"))
        val mine = opened(library = FakeRoomLibrary(CHART_A))
        val other = opened(uid = "u3", library = FakeRoomLibrary())
        assertEquals("sem", player("u3", "musica"))
        assertNull(mine.navigateToMatch.value)

        assertTrue(fake.repository("u2").startRound("ABCD"))
        runCurrent()

        assertEquals(RoomState.COUNTDOWN, mine.ready().room.state)
        assertEquals(MatchTicket(CHART_A, Difficulty.EXPERT, 1), mine.navigateToMatch.value)
        assertNull(other.navigateToMatch.value)
        assertTrue(other.ready().starting)

        mine.onMatchOpened()
        assertNull(mine.navigateToMatch.value)
        advanceTimeBy(5_001)
        assertNull(mine.navigateToMatch.value) // a mesma rodada não abre de novo
    }
}
