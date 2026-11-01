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
    fun theBoardComesWholeForTheMap() = runTest {
        server.answer = """{"ok":true,"linhas":[{"nome":"Rodrigo","voce":true,"dificuldade":"HARD","pontos":48300,
            "precisao":0.88,"combo":42,"perfeitos":70,"semErrar":null,"notas":85}]}"""

        val answer = RankingApi("https://x", server).board("k", "m1")

        assertTrue(server.lastJson.contains(""""acao":"placar""""))
        assertTrue(server.lastJson.contains(""""mapa":"m1""""))
        val entry = (answer as ApiAnswer.Ok).value.single()
        assertEquals(BoardEntry("Rodrigo", true, Difficulty.HARD, 48_300.0, 0.88, 42.0, 70.0, null, 85), entry)
    }

    @Test
    fun theSubmissionCarriesTheTotalNotes() = runTest {
        RankingApi("https://x", server).submit(
            "k",
            Submission("m1", Difficulty.HARD, "T", "A", 100, 0.9f, 5, 70, null, notes = 85),
        )

        assertTrue(server.lastJson.contains(""""notas":85"""))
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
