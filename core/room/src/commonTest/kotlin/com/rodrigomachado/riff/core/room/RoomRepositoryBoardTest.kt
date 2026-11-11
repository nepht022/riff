package com.rodrigomachado.riff.core.room

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.room.testing.FakeFirebase
import com.rodrigomachado.riff.core.room.testing.FakePlayer
import com.rodrigomachado.riff.core.room.testing.FirebaseWrite
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** O [RoomRepository] na M3: o placar da rodada (gravar o meu, ler o de todos, apagar a rodada antiga). */
class RoomRepositoryBoardTest : JsonTest() {
    private var now = T0
    private val fake = FakeFirebase(clock = { now }, uid = "u1")

    private fun keys(json: JSONObject) = json.keys().asSequence().toSet()

    private fun scoreWrites() = fake.writes.filter { it.path.contains("placar") }

    private fun player(uid: String, loadedRound: Int? = null) = FakePlayer(
        uid,
        name = uid.uppercase(),
        difficulty = Difficulty.HARD,
        ready = true,
        song = SongStatus.HAS,
        loadedRound = loadedRound,
    )

    /** A partida da rodada [round] com u1 (eu, o dono) e a bia, começada em [T0]. */
    private fun seedMatch(round: Int = 1, state: String = "partida") = fake.seedRoom(
        "ABCD",
        owner = "u1",
        players = listOf(player("u1", round), player("bia", round)),
        state = state,
        round = round,
        songJson = SONG_JSON,
        startAt = T0.takeIf { state == "partida" },
    )

    private suspend fun observed(repository: RoomRepository = fake.repository()) = repository.observe("ABCD").first()!!

    @Test
    fun publishWritesMyScore() = runTest {
        seedMatch()
        now = T0 + 3_000

        fake.repository().publishScore("ABCD", 1, RoundScore(4_200, 7, 31, 52, 60, 0.86667f, finished = false))

        val write = fake.writes.single()
        assertEquals("PUT" to "salas/ABCD/placar/1/u1", write.method to write.path)
        val body = JSONObject(write.body!!)
        assertEquals(
            setOf("pontos", "combo", "maiorCombo", "acertos", "notas", "precisao", "terminou", "atualizadoEm"),
            keys(body),
        )
        assertEquals(
            listOf(4_200L, 7L, 31L, 52L, 60L, 8_667L),
            listOf("pontos", "combo", "maiorCombo", "acertos", "notas", "precisao").map { body.getLong(it) },
        )
        assertFalse(body.getBoolean("terminou"))
        assertEquals("timestamp", body.getJSONObject("atualizadoEm").getString(".sv"))
        val line = observed().board.first()
        assertEquals(Triple("u1", "U1", 4_200L), Triple(line.uid, line.name, line.score))
        assertEquals(31, line.maxCombo)
        assertEquals(listOf("u1", "bia"), observed().board.map { it.uid }) // a bia ainda sem placar
    }

    @Test
    fun publishKeepsTheValuesInTheRulesLimits() = runTest {
        seedMatch()

        val repository = fake.repository()
        repository.publishScore("ABCD", 1, RoundScore(3_000_000_000, -1, 2_000_000, 5, 5, 1.5f, finished = true))
        repository.publishScore("ABCD", 0, RoundScore(1, 1, 1, 1, 1, 0.5f, finished = false)) // não há rodada 0

        val body = JSONObject(scoreWrites().single().body!!)
        assertEquals(
            listOf(1_000_000_000L, 0L, 1_000_000L, 10_000L),
            listOf("pontos", "combo", "maiorCombo", "precisao").map { body.getLong(it) },
        )
        assertTrue(body.getBoolean("terminou"))
    }

    @Test
    fun publishOfflineKeepsTheLastAndResends() = runTest {
        seedMatch()
        val repository = fake.repository()
        repository.heartbeat("ABCD") // o login feito antes de a internet cair
        fake.online = false

        repository.publishScore("ABCD", 1, RoundScore(100, 1, 1, 1, 2, 0.5f, finished = false)) // não lança
        repository.publishScore("ABCD", 1, RoundScore(900, 0, 8, 9, 10, 0.9f, finished = true))
        assertNull(fake.get("salas/ABCD/placar"))

        fake.online = true
        now += 20_000
        repository.heartbeat("ABCD") // a presença reenvia o último, mesmo sem outro publish (o final)
        val sent = fake.get("salas/ABCD/placar/1/u1") as JSONObject
        assertEquals(900L to true, sent.getLong("pontos") to sent.getBoolean("terminou"))
        assertEquals(now, sent.getLong("atualizadoEm"))
        val count = scoreWrites().size
        repository.heartbeat("ABCD")
        assertEquals(count, scoreWrites().size) // enviado: não manda de novo

        // o próximo publish também leva o que ficou guardado (de outra rodada também)
        fake.online = false
        repository.publishScore("ABCD", 1, RoundScore(950, 0, 8, 9, 10, 0.9f, finished = true))
        fake.online = true
        repository.publishScore("ABCD", 2, RoundScore(10, 1, 1, 1, 1, 1f, finished = false))
        assertEquals(950L, (fake.get("salas/ABCD/placar/1/u1") as JSONObject).getLong("pontos"))
        assertEquals(10L, (fake.get("salas/ABCD/placar/2/u1") as JSONObject).getLong("pontos"))
    }

    /** O placar guardado demora a subir (rede lenta): o batimento vai primeiro, com a hora de quando bateu. */
    @Test
    fun aSlowScoreDoesNotDelayTheHeartbeat() = runTest {
        seedMatch()
        val repository = fake.repository()
        repository.heartbeat("ABCD")
        fake.online = false
        repository.publishScore("ABCD", 1, RoundScore(900, 0, 8, 9, 10, 0.9f, finished = true))
        fake.online = true
        fake.onTrip = { method, path, arriving ->
            if (method == "PUT" && path.contains("placar") && arriving) now += 3_000
        }
        now += 10_000
        val beatAt = now

        repository.heartbeat("ABCD")

        assertEquals(beatAt, fake.get("salas/ABCD/jogadores/u1/vistoEm"))
        assertEquals(900L, (fake.get("salas/ABCD/placar/1/u1") as JSONObject).getLong("pontos")) // e o placar subiu
    }

    @Test
    fun refusedScoreIsNotResent() = runTest {
        seedMatch()
        val repository = fake.repository()
        fake.refuse = { it.path.contains("placar") } // por exemplo, uma rodada que já não vale

        repository.publishScore("ABCD", 1, RoundScore(100, 1, 1, 1, 2, 0.5f, finished = true))
        fake.refuse = { false }
        repository.heartbeat("ABCD")

        assertEquals(1, scoreWrites().size)
        assertNull(fake.get("salas/ABCD/placar"))
    }

    @Test
    fun leavingForgetsTheUnsentScore() = runTest {
        seedMatch()
        val repository = fake.repository()
        repository.heartbeat("ABCD")
        fake.online = false
        repository.publishScore("ABCD", 1, RoundScore(100, 1, 1, 1, 2, 0.5f, finished = true))
        repository.leave("ABCD")
        fake.online = true

        repository.heartbeat("ABCD")

        assertTrue(scoreWrites().isEmpty())
    }

    @Test
    fun startRoundDeletesTheRoundBeforeLast() = runTest {
        fake.seedRoom(
            "ABCD",
            owner = "u1",
            players = listOf(player("u1"), player("bia")),
            round = 2,
            songJson = SONG_JSON,
        )
        fake.seedScore("ABCD", 1, "u1", points = 10)
        fake.seedScore("ABCD", 2, "bia", points = 20)

        assertTrue(fake.repository().startRound("ABCD"))

        val write = fake.writes.last()
        assertEquals("PATCH" to "salas/ABCD", write.method to write.path)
        val body = JSONObject(write.body!!)
        assertEquals(setOf("estado", "rodada", "inicio", "atividadeEm", "placar/1"), keys(body))
        assertTrue(body.isNull("placar/1"))
        assertEquals(3, body.getInt("rodada"))
        assertEquals(setOf("2"), keys(fake.get("salas/ABCD/placar") as JSONObject))
        // a rodada que acabou continua à vista para o Resultado da sala
        assertEquals(listOf("bia"), observed().boardOf(2).map { it.uid })
    }

    /**
     * As regras antigas (sem o `placar`, não republicadas) recusam o PATCH inteiro com a deleção dele: o Começar
     * tenta de novo sem ela, e a rodada começa (o placar antigo fica no banco).
     */
    @Test
    fun startRoundWithTheOldRulesStartsWithoutTheDeletion() = runTest {
        fake.seedRoom(
            "ABCD",
            owner = "u1",
            players = listOf(player("u1"), player("bia")),
            round = 2,
            songJson = SONG_JSON,
        )
        fake.seedScore("ABCD", 1, "u1", points = 10)
        // pelas chaves (o org.json do Android escreve a barra como "\/")
        fake.refuse = { it.method == "PATCH" && keys(JSONObject(it.body!!)).any { key -> key.startsWith("placar/") } }

        assertTrue(fake.repository().startRound("ABCD"))

        assertEquals("contagem", fake.get("salas/ABCD/estado"))
        assertEquals(3, fake.get("salas/ABCD/rodada"))
        assertEquals(setOf("estado", "rodada", "inicio", "atividadeEm"), keys(JSONObject(fake.writes.last().body!!)))
        assertEquals(setOf("1"), keys(fake.get("salas/ABCD/placar") as JSONObject))
    }

    @Test
    fun startRoundOfTheFirstTwoRoundsDeletesNothing() = runTest {
        val writes = mutableListOf<FirebaseWrite>()
        for (round in 0..1) {
            fake.seedRoom(
                "ABCD",
                owner = "u1",
                players = listOf(player("u1"), player("bia")),
                round = round,
                songJson = SONG_JSON,
            )
            assertTrue(fake.repository().startRound("ABCD"))
            writes += fake.writes.last()
        }

        assertEquals(
            List(2) { setOf("estado", "rodada", "inicio", "atividadeEm") },
            writes.map { keys(JSONObject(it.body!!)) },
        )
    }

    @Test
    fun boardStaleUsesTheServerTime() = runTest {
        seedMatch()
        now = T0 + 20_000
        fake.seedScore("ABCD", 1, "u1", points = 50, updatedAt = now)
        fake.seedScore("ABCD", 1, "bia", points = 90, updatedAt = T0 + 2_000)
        // o relógio do aparelho 1 h atrasado: sem medir, vale o tempo mais novo gravado na sala
        val repository = fake.repository(deviceClock = { now - HOUR })

        val board = observed(repository).board

        assertEquals(listOf("bia" to true, "u1" to false), board.map { it.uid to it.stale })
        // com o relógio medido, o tempo passa mesmo sem ninguém gravar
        repository.measureClock("ABCD")
        now += 30_000
        assertTrue(observed(repository).board.all { it.stale })
    }

    @Test
    fun boardKeepsTheNameOfWhoLeftTheRoom() = runTest {
        seedMatch()
        fake.seedScore("ABCD", 1, "bia", points = 90)
        val repository = fake.repository()
        assertEquals("BIA", observed(repository).board.first().name)

        fake.set("salas/ABCD/jogadores/bia", "null") // saiu da sala

        val bia = observed(repository).board.last()
        assertEquals(Triple("bia", "BIA", true), Triple(bia.uid, bia.name, bia.left))
    }

    private companion object {
        const val T0 = 1_800_000_000_000L
        const val HOUR = 60 * 60_000L
        const val SONG_JSON = """{"chartId":"0123456789abcdef0123456789abcdef01234567","titulo":"Música",""" +
            """"artista":"Banda","dificuldades":"EASY,HARD,EXPERT","duracaoMs":215000}"""
    }
}
