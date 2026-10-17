package com.rodrigomachado.riff.core.data.ranking

import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RankingApiTest {
    private val server = object : RankingTransport {
        var lastJson = ""
        var answer = """{"ok":true}"""

        override suspend fun post(url: String, json: String): Pair<Int, String> {
            lastJson = json
            return 200 to answer
        }
    }

    @Test
    fun theBoardAsksWithOrderDirectionAndSearch() = runTest {
        server.answer = """{"ok":true,"dificuldades":["HARD","EXPERT"],"dificuldade":"HARD","total":27,
            "linhas":[{"posicao":3,"nome":"Rodrigo","valor":48300,"voce":true}]}"""

        val answer = RankingApi(
            "https://x",
            server,
        ).board("k", "m1", null, RankingMetric.COMBO, descending = false, "ro")

        assertTrue(server.lastJson.contains(""""ordem":"combo""""))
        assertTrue(server.lastJson.contains(""""sentido":"asc""""))
        assertTrue(server.lastJson.contains(""""busca":"ro""""))
        val board = (answer as ApiAnswer.Ok).value
        assertEquals(listOf(Difficulty.HARD, Difficulty.EXPERT), board.difficulties)
        assertEquals(BoardLine(3, "Rodrigo", 48_300.0, you = true), board.lines.single())
    }

    @Test
    fun songsComeWithPlayersAndYourPlace() = runTest {
        server.answer = """{"ok":true,"musicas":[{"mapa":"m1","titulo":"T","artista":"A","jogadores":27,
            "suaPosicao":3},{"mapa":"m2","titulo":"U","artista":"B","jogadores":8,"suaPosicao":null}]}"""

        val songs = (RankingApi("https://x", server).songs("k", "") as ApiAnswer.Ok).value

        assertEquals(RankedSong("m1", "T", "A", 27, 3), songs[0])
        assertEquals(null, songs[1].yourPosition)
    }

    @Test
    fun aRefusalCarriesTheReason() = runTest {
        server.answer = """{"ok":false,"motivo":"nome_em_uso"}"""

        assertEquals(ApiAnswer.Refused("nome_em_uso"), RankingApi("https://x", server).reserve("Ana", "k"))
    }
}
