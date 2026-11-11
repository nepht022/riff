package com.rodrigomachado.riff.feature.room

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.room.SongStatus
import com.rodrigomachado.riff.core.room.testing.FakeFirebase
import com.rodrigomachado.riff.core.room.testing.FakePlayer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** O começo da partida de sala pelo Firebase falso: carregou, o começo combinado no relógio da partida e o Sair. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomMatchStartTest {
    private var now = SERVER_T0
    private val fake = FakeFirebase(clock = { now }, uid = "u1")

    /** Quanto o relógio de parede foi acertado (pela rede) depois de aberto o teste. */
    private var wallCorrection = 0L

    /** O relógio de parede do aparelho: 5 min atrasado em relação ao servidor. */
    private val wallClock = { now - DEVICE_BEHIND_MS + wallCorrection }

    /** O relógio da partida (monotônico, desde que o aparelho ligou): nada a ver com os outros dois. */
    private val gameClock = { now - SERVER_T0 + GAME_T0 }

    private fun player(uid: String, loadedRound: Int? = null, leftRound: Int? = null) = FakePlayer(
        uid,
        difficulty = Difficulty.HARD,
        ready = true,
        song = SongStatus.HAS,
        loadedRound = loadedRound,
        leftRound = leftRound,
    )

    private fun seed(
        vararg players: FakePlayer,
        owner: String = "u1",
        state: String = "contagem",
        startAt: Long? = null,
    ) = fake.seedRoom("ABCD", owner, players.toList(), state = state, round = 1, songJson = SONG, startAt = startAt)

    /** O log de depuração do começo junto. */
    private val logged = mutableListOf<String>()

    private fun matchStart(uid: String = "u1") =
        RoomMatchStart("ABCD", 1, fake.repository(uid, deviceClock = wallClock), gameClock, log = { logged += it })

    private fun room() = fake.get("salas/ABCD") as JSONObject?

    private fun me(uid: String = "u1") = room()!!.getJSONObject("jogadores").getJSONObject(uid)

    @Test
    fun awaitStartReturnsLocalTimeOfInicio() = runTest {
        seed(
            player("u1", loadedRound = 1),
            player("bia", loadedRound = 1),
            owner = "bia",
            state = "partida",
            startAt = now + 3_000,
        )
        val start = matchStart()

        start.loaded()
        val at = start.awaitStart()

        // O começo é 3 s depois de agora no servidor: no relógio da partida, 3 s depois de agora também, mesmo com o
        // relógio de parede 5 min atrasado.
        assertEquals(gameClock() + 3_000, at)
    }

    /** O offset e a ida e volta da medida usada vão para o log de depuração (só números), para o teste do usuário. */
    @Test
    fun theClockMeasureIsLogged() = runTest {
        seed(
            player("u1", loadedRound = 1),
            player("bia", loadedRound = 1),
            owner = "bia",
            state = "partida",
            startAt = now + 3_000,
        )
        val start = matchStart()
        start.loaded()

        start.awaitStart()

        val line = logged.single()
        assertTrue(line, line.contains("offset=$DEVICE_BEHIND_MS ms"))
        assertTrue(line, line.contains("ida e volta=0 ms"))
        assertTrue(line, line.contains("começo em 3000 ms"))
    }

    /**
     * O relógio de parede acertado pela rede entre o carregou e o começo (o offset medido no carregou ficou errado):
     * o começo é medido de novo e continua certo no relógio da partida.
     */
    @Test
    fun aWallClockCorrectedAfterLoadingDoesNotMoveTheStart() = runTest {
        seed(
            player("u1", loadedRound = 1),
            player("bia", loadedRound = 1),
            owner = "bia",
            state = "partida",
            startAt = now + 3_000,
        )
        val start = matchStart()
        start.loaded()

        wallCorrection = DEVICE_BEHIND_MS // o relógio de parede agora está certo

        assertEquals(gameClock() + 3_000, start.awaitStart())
    }

    /**
     * Rede lenta: cada medida do relógio (o PATCH do `vistoEm` e o GET dele) leva 400 ms. Com o começo a 2 s, as
     * medidas param quando falta menos de 1 s para ele, e a partida começa no instante certo com as que deu tempo.
     */
    @Test
    fun slowClockMeasuresStopBeforeTheStart() = runTest {
        seed(
            player("u1", loadedRound = 1),
            player("bia", loadedRound = 1),
            owner = "bia",
            state = "partida",
            startAt = now + 2_000,
        )
        val start = matchStart()
        start.loaded()
        val expected = gameClock() + 2_000
        // a ida do PATCH e a volta do GET levam 200 ms cada: o carimbo do servidor cai no meio da ida e volta
        fake.onTrip = { method, _, arriving ->
            if ((method == "PATCH" && arriving) || (method == "GET" && !arriving)) now += 200
        }
        val measuresBefore = clockMeasures()

        val at = start.awaitStart()

        assertEquals(expected, at)
        assertEquals(3, clockMeasures() - measuresBefore) // 0, 400 e 800 ms; aos 1 200 ms falta menos de 1 s
        assertTrue("sobrou ${at - gameClock()} ms", at - gameClock() >= 500)
    }

    /** As medidas do relógio gravadas (o PATCH do `vistoEm` com a `atividadeEm`). */
    private fun clockMeasures() = fake.writes.count {
        it.method == "PATCH" && it.body.orEmpty().contains("vistoEm") && it.body.orEmpty().contains("atividadeEm")
    }

    @Test
    fun loadedMarksTheRound() = runTest {
        seed(player("u1"), player("bia"), owner = "bia")

        matchStart().loaded()

        assertEquals(1, me().getInt("carregou"))
    }

    @Test
    fun ownerSchedulesWhenAllLoaded() = runTest {
        seed(player("u1"), player("bia"))
        val start = matchStart()
        start.loaded()
        val at = async { start.awaitStart() }
        runCurrent()
        assertEquals("contagem", room()!!.getString("estado")) // falta a bia

        fake.repository("bia").markLoaded("ABCD", 1)
        advanceTimeBy(1_001) // o dono confere de novo a cada 1 s

        assertEquals("partida", room()!!.getString("estado"))
        assertEquals(now + 3_000, room()!!.getLong("inicio"))
        assertEquals(gameClock() + 3_000, at.await())
    }

    @Test
    fun ownerStartsWithWhoLoadedAfter15s() = runTest {
        seed(player("u1"), player("bia"))
        val start = matchStart()
        start.loaded()
        val at = async { start.awaitStart() }
        runCurrent()

        now += 15_000
        advanceTimeBy(1_001)

        assertEquals("partida", room()!!.getString("estado"))
        assertEquals(gameClock() + 3_000, at.await())
    }

    @Test
    fun guestOnlyWaitsForTheOwner() = runTest {
        seed(player("u1"), player("bia", loadedRound = 1), owner = "bia")
        val start = matchStart()
        start.loaded()
        val at = async { start.awaitStart() }
        advanceTimeBy(5_000)
        assertFalse(at.isCompleted)
        assertEquals("contagem", room()!!.getString("estado")) // quem marca o começo é o dono

        fake.repository("bia").scheduleStartIfDue("ABCD", 1)
        runCurrent()

        assertEquals(gameClock() + 3_000, at.await())
    }

    @Test
    fun aRoundThatWentBackToTheLobbyStartsRightAway() = runTest {
        seed(player("u1"), player("bia"), owner = "bia")
        val start = matchStart()
        start.loaded()
        val at = async { start.awaitStart() }
        runCurrent()

        fake.repository("bia").backToLobby("ABCD") // o dono saiu antes do começo

        runCurrent()
        assertEquals(gameClock(), at.await()) // a partida não fica presa esperando
    }

    @Test
    fun leavingMarksLeftAndTheOwnerGoesBackToTheLobby() = runTest {
        seed(player("u1", loadedRound = 1), player("bia", loadedRound = 1), state = "partida", startAt = now + 3_000)
        val start = matchStart()
        start.loaded()
        start.awaitStart()

        start.left()

        assertEquals(1, me().getInt("saiu"))
        assertEquals("sala", room()!!.getString("estado"))
        assertFalse(room()!!.has("inicio"))
    }

    @Test
    fun aGuestLeavingKeepsTheMatchGoing() = runTest {
        seed(
            player("u1", loadedRound = 1),
            player("bia", loadedRound = 1),
            owner = "bia",
            state = "partida",
            startAt = now + 3_000,
        )

        matchStart().left() // saiu antes de ver o começo

        assertEquals(1, me().getInt("saiu"))
        assertEquals("partida", room()!!.getString("estado"))
    }

    @Test
    fun theOwnerFinishingGoesBackToTheLobby() = runTest {
        seed(player("u1", loadedRound = 1), player("bia", loadedRound = 1), state = "partida", startAt = now + 3_000)
        val start = matchStart()
        start.loaded()
        start.awaitStart()

        val streams = fake.streamsOpened

        start.finished()

        assertEquals("sala", room()!!.getString("estado"))
        assertFalse(me().has("saiu"))
        assertEquals(streams, fake.streamsOpened)
    }

    @Test
    fun aGuestFinishingChangesNothing() = runTest {
        seed(
            player("u1", loadedRound = 1),
            player("bia", loadedRound = 1),
            owner = "bia",
            state = "partida",
            startAt = now + 3_000,
        )
        val start = matchStart()
        start.loaded()
        start.awaitStart()
        val writes = fake.writes.size
        val streams = fake.streamsOpened

        start.finished()

        assertEquals(writes, fake.writes.size)
        assertEquals(streams, fake.streamsOpened) // saber se sou o dono não abre outro streaming
        assertEquals("partida", room()!!.getString("estado"))
    }

    @Test
    fun withoutConnectionTheMatchStartsRightAway() = runTest {
        seed(player("u1"), player("bia"), owner = "bia")
        fake.online = false
        val start = matchStart()

        start.loaded()

        assertEquals(gameClock(), start.awaitStart())
        assertNull(me().opt("carregou"))
    }

    @Test
    fun aStartThatNeverComesStartsAfterTheLongestWait() = runTest {
        seed(player("u1"), player("bia"), owner = "bia") // o dono sumiu e ninguém assume
        val start = matchStart()
        start.loaded()
        val at = async { start.awaitStart() }

        advanceTimeBy(RoomMatchStart.MAX_WAIT_MS - 1)
        assertFalse(at.isCompleted)
        advanceTimeBy(2)

        assertTrue(at.isCompleted)
        assertEquals(gameClock(), at.await())
    }

    private companion object {
        const val SERVER_T0 = 1_800_000_000_000L
        const val GAME_T0 = 123_456L
        const val DEVICE_BEHIND_MS = 5 * 60_000L
        const val SONG = """{"chartId":"0123456789abcdef0123456789abcdef01234567","titulo":"Música",""" +
            """"artista":"Banda","dificuldades":"HARD"}"""
    }
}
