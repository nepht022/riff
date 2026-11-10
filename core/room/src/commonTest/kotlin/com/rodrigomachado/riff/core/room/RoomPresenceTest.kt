package com.rodrigomachado.riff.core.room

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.room.testing.FakeFirebase
import com.rodrigomachado.riff.core.room.testing.FakePlayer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A presença na sala ([RoomPresence]): o batimento enquanto o jogador está na sala, qualquer que seja a tela (a sala,
 * a partida, o Resultado, a Biblioteca), o Pronto que não sobra de uma rodada ou música anterior e a rodada órfã que o
 * dono desfaz. O relógio do banco é o tempo virtual do teste.
 */
@OptIn(ExperimentalCoroutinesApi::class) // o relógio virtual do runTest
class RoomPresenceTest : JsonTest() {
    private var scheduler: TestCoroutineScheduler? = null
    private val fake = FakeFirebase(clock = { T0 + (scheduler?.currentTime ?: 0L) }, uid = "u1")

    private fun room() = fake.get("salas/ABCD") as JSONObject?

    private fun players() = room()!!.getJSONObject("jogadores")

    private fun beats(uid: String) =
        fake.writes.count { it.uid == uid && it.method == "PATCH" && "jogadores/$uid/vistoEm" in it.body.orEmpty() }

    private fun ready(uid: String, seenAt: Long? = null, loadedRound: Int? = null) = FakePlayer(
        uid,
        seenAt = seenAt,
        difficulty = Difficulty.HARD,
        ready = true,
        song = SongStatus.HAS,
        loadedRound = loadedRound,
    )

    /** O jogador [uid] na sala (entrou por este repositório) com a presença ligada. */
    private suspend fun TestScope.present(uid: String): Pair<RoomRepository, RoomPresence> {
        scheduler = testScheduler
        val repository = fake.repository(uid)
        assertEquals(JoinResult.Joined("ABCD"), repository.join("ABCD", uid))
        val presence = RoomPresence(repository, backgroundScope)
        runCurrent()
        return repository to presence
    }

    private fun song(durationMs: Long? = TEN_MINUTES): String {
        val duration = durationMs?.let { ""","duracaoMs":$it""" }.orEmpty()
        return """{"chartId":"$CHART_A","titulo":"Música","artista":"Banda","dificuldades":"HARD"$duration}"""
    }

    /**
     * A partida longa (mais de 60 s, e mais de 5 min) com um terceiro jogador na sala: quem joga continua batendo,
     * então o dono não troca e ninguém é tirado da sala.
     */
    @Test
    fun aLongMatchKeepsTheOwnerAndEveryone() = runTest {
        fake.seedRoom(
            "ABCD",
            owner = "u1",
            players = listOf(ready("u1", loadedRound = 1), ready("bia", loadedRound = 1), FakePlayer("caio")),
            state = "partida",
            round = 1,
            songJson = song(),
            startAt = T0 + 3_000,
        )
        present("u1")
        present("bia")
        present("caio") // na sala, olhando a tela da sala: assumiria o dono se o dono sumisse

        advanceTimeBy(90_000)
        assertEquals("u1", room()!!.getString("dono"))

        advanceTimeBy(6 * MINUTE)
        assertEquals("u1", room()!!.getString("dono"))
        assertEquals(setOf("u1", "bia", "caio"), players().keys().asSequence().toSet())
        assertTrue(beats("u1") >= 20)
    }

    @Test
    fun beatsEvery20SecondsWhileInTheRoom() = runTest {
        fake.seedRoom("ABCD", owner = "u1", players = listOf(FakePlayer("u1")))
        present("u1")
        assertEquals(1, beats("u1"))

        advanceTimeBy(19_000)
        assertEquals(1, beats("u1"))
        advanceTimeBy(1_001)
        assertEquals(2, beats("u1"))
        advanceTimeBy(20_000)
        assertEquals(3, beats("u1"))
    }

    @Test
    fun leavingStopsTheHeartbeat() = runTest {
        fake.seedRoom("ABCD", owner = "bia", players = listOf(FakePlayer("bia"), FakePlayer("u1")))
        val (repository, _) = present("u1")
        val before = beats("u1")

        repository.leave("ABCD")
        advanceTimeBy(2 * MINUTE)

        assertEquals(before, beats("u1"))
        assertNull(repository.joined)
    }

    /** O app em segundo plano (celular): sem batimento; de volta, bate na hora. */
    @Test
    fun theBackgroundStopsTheHeartbeat() = runTest {
        fake.seedRoom("ABCD", owner = "u1", players = listOf(FakePlayer("u1")))
        val (_, presence) = present("u1")
        val before = beats("u1")

        presence.setForeground(false)
        advanceTimeBy(2 * MINUTE)
        assertEquals(before, beats("u1"))

        presence.setForeground(true)
        runCurrent()
        assertEquals(before + 1, beats("u1"))
    }

    /** De volta à sala depois da rodada: o meu Pronto (de antes) volta a "não", mesmo sem a tela da sala à vista. */
    @Test
    fun readyFromThePreviousRoundIsCleared() = runTest {
        fake.seedRoom(
            "ABCD",
            owner = "bia",
            players = listOf(ready("bia", loadedRound = 1), ready("u1", loadedRound = 1)),
            state = "partida",
            round = 1,
            songJson = song(),
            startAt = T0 + 3_000,
        )
        present("u1")
        assertEquals(true, players().getJSONObject("u1").getBoolean("pronto"))

        fake.repository("bia").backToLobby("ABCD")
        runCurrent()

        assertEquals(false, players().getJSONObject("u1").getBoolean("pronto"))
        val write = fake.writes.last { it.uid == "u1" }
        assertEquals(setOf("jogadores/u1/pronto", "atividadeEm"), JSONObject(write.body!!).keys().asSequence().toSet())

        // Pronto de novo depois disso: fica.
        fake.repository("u1").setMe("ABCD", Difficulty.HARD, ready = true, song = SongStatus.HAS)
        advanceTimeBy(MINUTE)
        assertEquals(true, players().getJSONObject("u1").getBoolean("pronto"))
    }

    /** A rodada que passou inteira com o app em segundo plano também conta (a rodada subiu). */
    @Test
    fun aRoundMissedInTheBackgroundAlsoClearsReady() = runTest {
        fake.seedRoom(
            "ABCD",
            owner = "bia",
            players = listOf(ready("bia"), ready("u1")),
            round = 1,
            songJson = song(),
        )
        val (_, presence) = present("u1")
        presence.setForeground(false)
        fake.set("salas/ABCD/rodada", "2") // a rodada 2 começou e acabou sem eu ver
        runCurrent()
        assertEquals(true, players().getJSONObject("u1").getBoolean("pronto"))

        presence.setForeground(true)
        runCurrent()

        assertEquals(false, players().getJSONObject("u1").getBoolean("pronto"))
    }

    /** Outra música (o dono trocou com a minha tela da sala fora da vista): o Pronto volta a "não". */
    @Test
    fun aNewSongClearsReady() = runTest {
        fake.seedRoom("ABCD", owner = "bia", players = listOf(ready("bia"), ready("u1")), songJson = song())
        present("u1")
        assertEquals(true, players().getJSONObject("u1").getBoolean("pronto")) // a sala aberta já com o Pronto

        val other = RoomSong(CHART_B, "Outra", "Banda", setOf(Difficulty.HARD), null, null)
        fake.repository("bia").chooseSong("ABCD", other, Difficulty.HARD)
        runCurrent()

        assertEquals(false, players().getJSONObject("u1").getBoolean("pronto"))
    }

    /** O dono desfaz a contagem em que ninguém carregou (a partida nunca começou): a sala volta à escolha. */
    @Test
    fun theOwnerUndoesAnOrphanCountdown() = runTest {
        fake.seedRoom(
            "ABCD",
            owner = "u1",
            players = listOf(ready("u1"), ready("bia")),
            state = "contagem",
            round = 1,
            songJson = song(),
        )
        present("u1")

        advanceTimeBy(MINUTE)

        assertEquals("sala", room()!!.getString("estado"))
    }

    private companion object {
        const val T0 = 1_800_000_000_000L
        const val MINUTE = 60_000L
        const val TEN_MINUTES = 10 * MINUTE
        const val CHART_A = "0123456789abcdef0123456789abcdef01234567"
        const val CHART_B = "89abcdef0123456789abcdef0123456789abcdef"
    }
}
