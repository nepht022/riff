package com.rodrigomachado.riff.core.data.ranking

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.RankingTable
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun sent() = JSONObject(server.lastJson)

    @Test
    fun theSubmissionSaysItsTable() = runTest {
        RankingApi("https://x", server).submit(
            "k",
            Submission("m1", Difficulty.HARD, "T", "A", 100, 0.9f, 5, 70, null, table = RankingTable.PC_STRUM),
        )

        assertEquals("pc_palheta", sent().getString("tabela"))
    }

    @Test
    fun eachSubmissionInTheBatchSaysItsTable() = runTest {
        server.answer = """{"ok":true,"resultados":[{"ok":true,"posicao":1,"total":1},{"ok":true,"posicao":2,
            "total":2}]}"""
        val phone = Submission("m1", Difficulty.HARD, "T", "A", 100, 0.9f, 5, 70, null)

        RankingApi("https://x", server).submitMany("k", listOf(phone, phone.copy(table = RankingTable.PC_NO_STRUM)))

        val envios = sent().getJSONArray("envios")
        assertEquals("celular", envios.getJSONObject(0).getString("tabela"))
        assertEquals("pc_sem_palheta", envios.getJSONObject(1).getString("tabela"))
    }

    /** O placar pede todas as tabelas; sem a coluna (script antigo) é celular; tabela desconhecida fica de fora. */
    @Test
    fun theBoardAsksForEveryTableAndReadsEachLineTable() = runTest {
        server.answer = """{"ok":true,"linhas":[
            {"nome":"Ana","voce":false,"dificuldade":"HARD","pontos":1,"precisao":1,"combo":1,"perfeitos":1,
             "semErrar":null,"notas":1,"tabela":"pc_palheta"},
            {"nome":"Bia","voce":false,"dificuldade":"HARD","pontos":2,"precisao":1,"combo":1,"perfeitos":1,
             "semErrar":null,"notas":1},
            {"nome":"Caio","voce":false,"dificuldade":"HARD","pontos":3,"precisao":1,"combo":1,"perfeitos":1,
             "semErrar":null,"notas":1,"tabela":"tablet"}]}"""

        val lines = (RankingApi("https://x", server).board("k", "m1") as ApiAnswer.Ok).value

        assertTrue(sent().getBoolean("tabelas"))
        assertEquals(
            listOf("Ana" to RankingTable.PC_STRUM, "Bia" to RankingTable.PHONE),
            lines.map { it.name to it.table },
        )
    }

    @Test
    fun songsComeWithTheirStandingInEachTable() = runTest {
        server.answer = """{"ok":true,"musicas":[{"mapa":"m1","titulo":"T","artista":"A","jogadores":5,
            "suaPosicao":2,"tabelas":{"celular":{"jogadores":3,"suaPosicao":null},
            "pc_palheta":{"jogadores":2,"suaPosicao":2}}}]}"""

        val song = (RankingApi("https://x", server).songs("k", "") as ApiAnswer.Ok).value.single()

        assertTrue(sent().getBoolean("tabelas"))
        assertEquals(TableStanding(3, null), song.inTable(RankingTable.PHONE))
        assertEquals(TableStanding(2, 2), song.inTable(RankingTable.PC_STRUM))
        assertEquals(null, song.inTable(RankingTable.PC_NO_STRUM))
    }

    /** O script antigo não manda as tabelas: tudo o que vier é do celular. */
    @Test
    fun songsFromTheOldScriptAreAllPhone() = runTest {
        server.answer = """{"ok":true,"musicas":[{"mapa":"m1","titulo":"T","artista":"A","jogadores":4,
            "suaPosicao":1}]}"""

        val song = (RankingApi("https://x", server).songs("k", "") as ApiAnswer.Ok).value.single()

        assertEquals(TableStanding(4, 1), song.inTable(RankingTable.PHONE))
        assertEquals(null, song.inTable(RankingTable.PC_STRUM))
    }

    @Test
    fun aLinkCodeComesWithHowLongItLasts() = runTest {
        server.answer = """{"ok":true,"codigo":"ABC234","validoAte":"2026-11-04T12:10:00.000Z","validoPor":600}"""

        val answer = RankingApi("https://x", server).linkCode("k")

        assertEquals("codigo", sent().getString("acao"))
        assertEquals("k", sent().getString("chave"))
        assertEquals(ApiAnswer.Ok(LinkCode("ABC234", 600)), answer)
    }

    @Test
    fun linkingSendsTheCodeAndGetsTheName() = runTest {
        server.answer = """{"ok":true,"nome":"Rodrigo"}"""

        val answer = RankingApi("https://x", server).link("k", "ABC234")

        assertEquals("vincular", sent().getString("acao"))
        assertEquals("ABC234", sent().getString("codigo"))
        assertEquals(ApiAnswer.Ok("Rodrigo"), answer)
    }

    @Test
    fun theOldScriptRefusesTheLinkAsABadRequest() = runTest {
        server.answer = """{"ok":false,"motivo":"pedido"}"""

        assertEquals(ApiAnswer.Refused("pedido"), RankingApi("https://x", server).linkCode("k"))
        assertEquals(ApiAnswer.Refused("pedido"), RankingApi("https://x", server).link("k", "ABC234"))
        assertFalse(server.lastJson.isEmpty())
    }
}
