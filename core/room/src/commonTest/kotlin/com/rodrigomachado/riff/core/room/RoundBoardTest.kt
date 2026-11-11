package com.rodrigomachado.riff.core.room

import com.rodrigomachado.riff.core.model.Difficulty
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** O placar da rodada ([RoundBoard]) sobre a [Room] pura: a ordem, quem saiu, o "sem conexão" e o fim da rodada. */
class RoundBoardTest : JsonTest() {
    private fun player(uid: String, joinedAt: Long, leftRound: Int? = null) = RoomPlayer(
        uid = uid,
        name = uid.replaceFirstChar { it.uppercase() },
        device = if (uid == "bia") RoomDevice.PC else RoomDevice.PHONE,
        joinedAt = joinedAt,
        seenAt = START,
        gone = false,
        difficulty = Difficulty.HARD,
        ready = true,
        song = SongStatus.HAS,
        loadedRound = ROUND,
        leftRound = leftRound,
    )

    private val ana = player("ana", joinedAt = 1)
    private val bia = player("bia", joinedAt = 2)
    private val caio = player("caio", joinedAt = 3)

    private fun room(vararg players: RoomPlayer, durationMs: Long? = SONG_MS) = Room(
        code = "ABCD",
        owner = "ana",
        players = players.toList().ifEmpty { listOf(ana, bia, caio) },
        me = "ana",
        song = RoomSong(
            "0123456789abcdef0123456789abcdef01234567",
            "Música",
            "Banda",
            setOf(Difficulty.HARD),
            durationMs,
            null,
        ),
        round = ROUND,
        state = RoomState.PLAYING,
        startAt = START,
    )

    private fun score(
        points: Long,
        maxCombo: Int = 10,
        combo: Int = 0,
        finished: Boolean = false,
        updatedAt: Long = START + 5_000,
    ) = WrittenScore(
        RoundScore(points, combo, maxCombo, hits = 50, notes = 60, accuracy = 0.8333f, finished),
        updatedAt,
    )

    /** A sala com o placar da rodada lido na hora do servidor [now]. */
    private fun board(room: Room, scores: Map<String, WrittenScore>, now: Long = START + 5_000): Room =
        room.copy(boards = mapOf(ROUND to RoundBoard.lines(room, ROUND, scores, now)))

    @Test
    fun boardIsOrderedByScoreThenCombo() {
        val room = board(
            room(),
            mapOf("ana" to score(1_000, maxCombo = 5), "bia" to score(3_000), "caio" to score(1_000, maxCombo = 9)),
        )

        assertEquals(listOf("bia", "caio", "ana"), room.board.map { it.uid })
        val bia = room.board.first()
        assertEquals("Bia" to RoomDevice.PC, bia.name to bia.device)
        assertEquals(3_000L, bia.score)
        assertEquals(0.8333f, bia.accuracy, 0.0001f)
        assertFalse(bia.finished || bia.left || bia.stale)
    }

    @Test
    fun tiesAreStable() {
        // mesmos pontos e mesmo maior combo: quem entrou primeiro, qualquer que seja o combo de agora ou a ordem lida
        val scores = mapOf("caio" to score(2_000, combo = 9), "bia" to score(2_000, combo = 1), "ana" to score(500))
        val first = board(room(), scores).board.map { it.uid }
        val later = board(room(), scores.mapValues { (uid, s) -> if (uid == "bia") score(2_000, combo = 10) else s })

        assertEquals(listOf("bia", "caio", "ana"), first)
        assertEquals(first, later.board.map { it.uid })
    }

    @Test
    fun leftGoesLastAndDoesNotHoldTheEnd() {
        val room = room(ana, player("bia", joinedAt = 2, leftRound = ROUND), caio)
        // a bia saiu na frente; o caio saiu da sala (não está mais em jogadores), mas o placar dele ficou
        val scores = mapOf(
            "ana" to score(1_000, finished = true),
            "bia" to score(9_000),
            "zeca" to score(8_000),
        )
        val withBoard = board(room, scores)

        // o caio (ainda sem placar) fica à frente de quem saiu, mesmo com menos pontos
        assertEquals(listOf("ana", "caio", "bia", "zeca"), withBoard.board.map { it.uid })
        assertEquals(listOf(false, false, true, true), withBoard.board.map { it.left })
        assertEquals("", withBoard.board.last().name) // saiu da sala antes de este aparelho vê-lo
        assertFalse(withBoard.roundOver(START + 5_000)) // o caio ainda joga; os que saíram não seguram
        val caioDone = board(room, scores + ("caio" to score(700, finished = true)))
        assertEquals(listOf("ana", "caio", "bia", "zeca"), caioDone.board.map { it.uid })
        assertTrue(caioDone.roundOver(START + 5_000))
    }

    @Test
    fun staleScoreShowsNoConnection() {
        val now = START + 40_000
        val scores = mapOf(
            "ana" to score(1_000, updatedAt = now - 1_000),
            "bia" to score(2_000, updatedAt = now - 16_000), // parou de mandar há 16 s
            "zeca" to score(500, finished = true, updatedAt = START + 2_000), // terminou: não manda mais, e tudo bem
        )
        val room = board(room(ana, bia, caio, player("zeca", joinedAt = 4)), scores, now)

        val stale = room.board.associate { it.uid to it.stale }
        assertEquals(mapOf("ana" to false, "bia" to true, "zeca" to false, "caio" to true), stale) // caio nunca mandou
        // nunca mandou, mas a partida começou há menos de 15 s: ainda não
        val early = board(room(ana, caio), mapOf("ana" to score(10)), START + 10_000)
        assertFalse(early.board.single { it.uid == "caio" }.stale)
        // na contagem (sem começo marcado) ninguém está sem conexão
        assertFalse(board(room(ana, caio).copy(startAt = null), emptyMap(), now).board.any { it.stale })
    }

    @Test
    fun roundIsOverWhenAllFinishedOrLeft() {
        val room = room(ana, player("bia", joinedAt = 2, leftRound = ROUND), caio)
        val playing = board(room, mapOf("ana" to score(10, finished = true), "caio" to score(20)))
        val done = board(room, mapOf("ana" to score(10, finished = true), "caio" to score(20, finished = true)))

        assertFalse(playing.roundOver(START + 5_000))
        assertTrue(done.roundOver(START + 5_000))
        assertFalse(room().roundOver(START + 5_000)) // sem placar lido: ninguém terminou
    }

    @Test
    fun roundIsOverTenSecondsAfterTheSong() {
        val room = board(room(), mapOf("ana" to score(10, finished = true)))

        assertFalse(room.roundOver(START + SONG_MS + 9_999))
        assertTrue(room.roundOver(START + SONG_MS + 10_000))
        assertFalse(room.copy(startAt = null).roundOver(START + SONG_MS * 10)) // sem começo, só pelos que terminaram
        // sem a duração da música: 15 min
        val unknown = room(durationMs = null).copy(boards = room.boards)
        assertFalse(unknown.roundOver(START + SONG_MS + 10_000))
        assertTrue(unknown.roundOver(START + RoomRound.UNKNOWN_SONG_MS + 10_000))
    }

    @Test
    fun boardOfAnotherRoundAndTheArrayFromFirebase() {
        // o Firebase devolve {"1":…,"2":…} como lista quando as chaves são números seguidos
        val json = JSONObject(
            """{"placar":[null,{"ana":{"pontos":5,"combo":1,"maiorCombo":2,"acertos":3,"notas":4,"precisao":7500,""" +
                """"terminou":true,"atualizadoEm":7}},{"bia":{"pontos":9}}]}""",
        )

        val scores = ScoreFormat.scores(json)

        assertEquals(setOf(1, 2), scores.keys)
        assertEquals(RoundScore(5, 1, 2, 3, 4, 0.75f, true), scores.getValue(1).getValue("ana").score)
        assertEquals(7L, scores.getValue(1).getValue("ana").updatedAt)
        val past = RoundBoard.lines(room(), 1, scores.getValue(1), START)
        // a rodada que já passou: quem não mandou o final está sem conexão
        assertEquals(listOf("ana"), past.map { it.uid })
        assertEquals(listOf(true), past.map { it.finished })
        val room = room().copy(round = 2, boards = mapOf(1 to past))
        assertEquals(past, room.boardOf(1))
        assertTrue(room.board.isEmpty())
    }

    private companion object {
        const val ROUND = 3
        const val START = 1_800_000_000_000L
        const val SONG_MS = 200_000L
    }
}
