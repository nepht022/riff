package com.rodrigomachado.riff.feature.room

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.room.Room
import com.rodrigomachado.riff.core.room.RoomRound
import com.rodrigomachado.riff.core.room.RoundBoard
import com.rodrigomachado.riff.core.room.SongStatus
import com.rodrigomachado.riff.core.room.testing.FakeFirebase
import com.rodrigomachado.riff.core.room.testing.FakePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * O Resultado da sala ([RoomResultsViewModel]) com o Firebase falso: a classificação final da rodada, ao vivo (o
 * placar final atrasado aparece quando chega), "saiu" e "sem conexão". Só `runCurrent`: o streaming da sala tem um
 * relógio sem fim (a cada 5 s), e esperar tudo acabar nunca acabaria.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomResultsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val fake = FakeFirebase(clock = { T0 }, uid = "u1")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fake.seedRoom(
            "ABCD",
            "u1",
            listOf(player("u1", "Rodrigo"), player("ana", "Ana"), player("bia", "Bia")),
            state = "sala",
            round = 1,
            startAt = T0 - SONG_AND_GRACE_MS,
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun player(uid: String, name: String, leftRound: Int? = null) = FakePlayer(
        uid,
        name = name,
        difficulty = Difficulty.HARD,
        ready = false,
        song = SongStatus.HAS,
        loadedRound = 1,
        leftRound = leftRound,
    )

    // A hora do servidor estimada que o Resultado usa para o fim da rodada (null = ainda sem medir).
    private var serverNow: Long? = null

    /** A tela aberta (alguém olhando o estado), lendo a sala como a presença lê: o streaming do repositório. */
    private fun TestScope.opened(round: Int = 1): RoomResultsViewModel {
        val repository = fake.repository("u1")
        val vm = RoomResultsViewModel("ABCD", round, flow { emitAll(repository.observe("ABCD")) }) { serverNow }
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        return vm
    }

    private fun RoomResultsViewModel.ready() = uiState.value as RoomResultsUiState.Ready

    @Test
    fun roomResultsShowTheFinalOrder() = runTest(dispatcher) {
        fake.seedScore("ABCD", 1, "u1", points = 5_000, maxCombo = 10, accuracy = 8_000, finished = true)
        fake.seedScore("ABCD", 1, "ana", points = 9_000, maxCombo = 40, accuracy = 9_500, finished = true)
        fake.seedScore("ABCD", 1, "bia", points = 5_000, maxCombo = 30, accuracy = 7_000, finished = true)

        val state = opened().ready()

        assertEquals(listOf("Ana", "Bia", "Rodrigo"), state.lines.map { it.name })
        assertEquals(9_000L, state.lines[0].score)
        assertEquals(0.95f, state.lines[0].accuracy)
        assertEquals(40, state.lines[0].maxCombo)
        assertEquals("u1", state.me)
        assertEquals(1, state.round)
        assertFalse("ninguém mais jogando", state.waiting)
    }

    /** Conexão ruim: o final da Ana chega depois do fim da rodada, e o Resultado da sala já aberto atualiza. */
    @Test
    fun lateFinalScoreUpdatesTheRoomResults() = runTest(dispatcher) {
        fake.seedScore("ABCD", 1, "u1", points = 5_000, finished = true)
        fake.seedScore("ABCD", 1, "ana", points = 1_000, updatedAt = T0 - STALE_AGO_MS)
        fake.seedScore("ABCD", 1, "bia", points = 2_000, finished = true)
        val vm = opened()
        val before = vm.ready().lines.single { it.uid == "ana" }
        assertTrue("sem o final, a Ana aparece sem conexão", before.stale)
        assertEquals("Ana", vm.ready().lines.last().name)

        fake.seedScore("ABCD", 1, "ana", points = 9_000, maxCombo = 50, finished = true)
        runCurrent()

        val after = vm.ready().lines
        assertEquals(listOf("Ana", "Rodrigo", "Bia"), after.map { it.name })
        assertTrue(after[0].finished)
        assertFalse(after[0].stale)
        assertEquals(9_000L, after[0].score)
    }

    @Test
    fun leftAndNoConnectionAreShown() = runTest(dispatcher) {
        fake.seedRoom(
            "ABCD",
            "u1",
            listOf(player("u1", "Rodrigo"), player("ana", "Ana", leftRound = 1), player("bia", "Bia")),
            state = "sala",
            round = 1,
            startAt = T0 - SONG_AND_GRACE_MS,
        )
        fake.seedScore("ABCD", 1, "u1", points = 5_000, finished = true)
        fake.seedScore("ABCD", 1, "ana", points = 9_000)
        fake.seedScore("ABCD", 1, "bia", points = 3_000, updatedAt = T0 - STALE_AGO_MS)

        val lines = opened().ready().lines

        assertEquals(listOf("Rodrigo", "Bia", "Ana"), lines.map { it.name })
        assertTrue("quem saiu fica por último", lines[2].left)
        assertTrue("a Bia sem mandar o final", lines[1].stale)
    }

    /** Alguém ainda terminando (o placar dele chegando): o Resultado avisa que espera. */
    @Test
    fun waitingWhileSomeoneStillPlays() = runTest(dispatcher) {
        fake.seedScore("ABCD", 1, "u1", points = 5_000, finished = true)
        fake.seedScore("ABCD", 1, "ana", points = 1_000)
        fake.seedScore("ABCD", 1, "bia", points = 2_000, finished = true)
        val vm = opened()
        assertTrue(vm.ready().waiting)

        fake.seedScore("ABCD", 1, "ana", points = 1_500, finished = true)
        runCurrent()

        assertFalse(vm.ready().waiting)
    }

    /**
     * A rodada acabou pelo tempo ([Room.roundOver]: 10 s depois do fim da música) com a Ana ainda mandando o placar
     * (a conexão dela, boa, mas o final não veio): o Resultado para de esperar.
     */
    @Test
    fun stopsWaitingWhenTheRoundIsOver() = runTest(dispatcher) {
        fake.seedScore("ABCD", 1, "u1", points = 5_000, finished = true)
        fake.seedScore("ABCD", 1, "ana", points = 1_000)
        fake.seedScore("ABCD", 1, "bia", points = 2_000, finished = true)
        val startAt = T0 - SONG_AND_GRACE_MS
        serverNow = startAt + RoomRound.UNKNOWN_SONG_MS + RoundBoard.END_GRACE_MS - 1
        val vm = opened()
        assertTrue(vm.ready().waiting)

        serverNow = startAt + RoomRound.UNKNOWN_SONG_MS + RoundBoard.END_GRACE_MS
        advanceTimeBy(RECHECK_MS) // a sala não muda: o Resultado confere sozinho

        assertFalse(vm.ready().waiting)
        assertFalse(vm.ready().lines.single { it.uid == "ana" }.stale)
    }

    /** O dono já começou a rodada seguinte: o Resultado continua sendo o da rodada da partida. */
    @Test
    fun keepsTheRoundOfTheMatch() = runTest(dispatcher) {
        fake.seedScore("ABCD", 1, "u1", points = 5_000, finished = true)
        fake.seedScore("ABCD", 1, "ana", points = 9_000, finished = true)
        fake.seedScore("ABCD", 2, "ana", points = 100)
        fake.set("salas/ABCD/rodada", "2")

        val state = opened(round = 1).ready()

        assertEquals(listOf(9_000L, 5_000L, 0L), state.lines.map { it.score })
    }

    /** A presença sem a sala por um instante (trocando, em segundo plano): o Resultado não pisca vazio. */
    @Test
    fun aMissingRoomKeepsTheLastResults() = runTest(dispatcher) {
        fake.seedScore("ABCD", 1, "u1", points = 5_000, finished = true)
        val repository = fake.repository("u1")
        val rooms = MutableStateFlow<Room?>(null)
        val vm = RoomResultsViewModel("ABCD", 1, rooms)
        backgroundScope.launch { vm.uiState.collect {} }
        backgroundScope.launch { repository.observe("ABCD").collect { rooms.value = it } }
        runCurrent()
        assertEquals(3, vm.ready().lines.size)

        rooms.value = null
        runCurrent()

        assertEquals(3, vm.ready().lines.size)
    }

    private companion object {
        const val T0 = 1_800_000_000_000L
        const val SONG_AND_GRACE_MS = 200_000L
        const val STALE_AGO_MS = 20_000L
        const val RECHECK_MS = 1_001L
    }
}
