package com.rodrigomachado.riff.feature.room

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.room.RoundScore
import com.rodrigomachado.riff.core.room.SongStatus
import com.rodrigomachado.riff.core.room.testing.FakeFirebase
import com.rodrigomachado.riff.core.room.testing.FakePlayer
import com.rodrigomachado.riff.feature.gameplay.LiveScore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** O placar ao vivo da sala na partida ([RoomLiveBoard]) pelo Firebase falso: o dos outros e o envio do meu. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomLiveBoardTest {
    private var scheduler: TestCoroutineScheduler? = null
    private val fake = FakeFirebase(clock = { T0 + (scheduler?.currentTime ?: 0L) }, uid = "u1")

    private fun player(uid: String, leftRound: Int? = null) = FakePlayer(
        uid,
        name = uid.replaceFirstChar { it.uppercase() },
        difficulty = Difficulty.HARD,
        ready = true,
        song = SongStatus.HAS,
        loadedRound = 1,
        leftRound = leftRound,
    )

    private fun seed() = fake.seedRoom(
        "ABCD",
        "u1",
        listOf(player("u1"), player("ana"), player("bia", leftRound = 1)),
        state = "partida",
        round = 1,
        songJson = SONG,
        startAt = T0,
    )

    private fun mine(score: Long, finished: Boolean = false) =
        LiveScore("", "", score, 3, 7, 0.25f, finished, hits = 5, notes = 8)

    @Test
    fun othersAreTheRoundBoardWithoutMe() = runTest {
        scheduler = testScheduler
        seed()
        fake.seedScore("ABCD", 1, "ana", points = 1_500, combo = 4, maxCombo = 9, accuracy = 5_000)
        fake.seedScore("ABCD", 1, "u1", points = 9_000)
        val repository = fake.repository("u1")
        val board = RoomLiveBoard("ABCD", 1, repository, repository.observe("ABCD"), backgroundScope)

        val others = board.others.first()

        assertEquals(listOf("ana", "bia"), others.map { it.uid })
        val ana = others[0]
        assertEquals("Ana", ana.name)
        assertEquals(1_500L, ana.score)
        assertEquals(4, ana.combo)
        assertEquals(9, ana.maxCombo)
        assertEquals(0.5f, ana.accuracy)
        assertTrue(others[1].left)
    }

    @Test
    fun publishWritesMyScoreForTheRound() = runTest {
        scheduler = testScheduler
        seed()
        val repository = fake.repository("u1")
        val board = RoomLiveBoard("ABCD", 1, repository, repository.observe("ABCD"), backgroundScope)

        board.publish(mine(4_200, finished = true))
        runCurrent()

        val written = fake.get("salas/ABCD/placar/1/u1") as JSONObject
        assertEquals(4_200L, written.getLong("pontos"))
        assertEquals(3, written.getInt("combo"))
        assertEquals(7, written.getInt("maiorCombo"))
        assertEquals(5, written.getInt("acertos"))
        assertEquals(8, written.getInt("notas"))
        assertEquals(2_500, written.getInt("precisao"))
        assertTrue(written.getBoolean("terminou"))
    }

    /**
     * Sem internet (o servidor que não responde): publicar volta na hora, o envio fica fora de quem chamou (o quadro
     * da partida) e não empilha: com um envio preso, os seguintes esperam e só o último vai depois.
     */
    @Test
    fun offlinePublishNeverBlocksTheFrame() = runTest {
        val gate = CompletableDeferred<Unit>()
        val sent = mutableListOf<RoundScore>()
        var inFlight = 0
        var mostInFlight = 0
        val board = RoomLiveBoard("ABCD", 1, emptyFlow(), backgroundScope) { score ->
            inFlight++
            mostInFlight = maxOf(mostInFlight, inFlight)
            sent += score
            if (sent.size == 1) gate.await()
            inFlight--
        }

        board.publish(mine(100))
        assertTrue(sent.isEmpty()) // não envia na thread de quem chamou
        runCurrent()
        assertEquals(1, sent.size) // preso no primeiro envio

        board.publish(mine(200))
        board.publish(mine(300, finished = true))
        runCurrent()
        assertEquals(1, sent.size)

        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(100L, 300L), sent.map { it.score })
        assertTrue(sent.last().finished)
        assertEquals(1, mostInFlight)

        // Depois de esvaziar, o próximo vai de novo.
        board.publish(mine(400))
        runCurrent()
        assertEquals(400L, sent.last().score)
    }

    /**
     * Um erro inesperado no envio (não a falta de internet, que o repositório já engole) só vai para o log: não
     * derruba o app (no celular, uma exceção sem tratamento no escopo fecha o app) e o próximo envio vai.
     */
    @Test
    fun anErrorInTheSendIsOnlyLogged() = runTest {
        val logged = mutableListOf<Throwable>()
        val sent = mutableListOf<Long>()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val board = RoomLiveBoard("ABCD", 1, emptyFlow(), scope, log = { _, error -> logged += error }) { score ->
            check(score.score != 100L) { "falha no envio" }
            sent += score.score
        }

        board.publish(mine(100))
        runCurrent()
        board.publish(mine(200, finished = true))
        runCurrent()

        assertEquals(listOf("falha no envio"), logged.map { it.message })
        assertEquals(listOf(200L), sent)
        assertTrue(scope.isActive)
        scope.cancel()
    }

    /** Com o repositório de verdade e o servidor travado: publicar continua voltando na hora. */
    @Test
    fun aStalledServerDoesNotHoldThePublish() {
        seed()
        fake.stalled = true
        val repository = fake.repository("u1")
        val board = RoomLiveBoard("ABCD", 1, repository, emptyFlow())

        val started = System.nanoTime()
        repeat(10) { board.publish(mine(it * 100L)) }
        val tookMs = (System.nanoTime() - started) / 1_000_000

        assertTrue("publicar levou $tookMs ms", tookMs < 100)
    }

    private companion object {
        const val T0 = 1_800_000_000_000L
        const val SONG = """{"chartId":"0123456789abcdef0123456789abcdef01234567","titulo":"Música",""" +
            """"artista":"Banda","dificuldades":"HARD","duracaoMs":180000}"""
    }
}
