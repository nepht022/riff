package com.rodrigomachado.riff.core.data.ranking

import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.database.RankingPendingDao
import com.rodrigomachado.riff.core.database.RankingPendingEntity
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.RankingTable
import com.rodrigomachado.riff.core.model.ScoreRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
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
import java.io.IOException

/** Servidor falso com o protocolo do scripts/ranking_apps_script.gs. */
class FakeRankingServer : RankingTransport {
    val calls = mutableListOf<JSONObject>()
    val names = mutableMapOf<String, String>() // nome em minúsculas → chave
    var offline = false
    var answerHtml = false

    /** O script grava, mas a resposta se perde (Apps Script lento, conexão caiu). */
    var loseAnswer = false

    /** O script de antes do "enviarVarios": responde "pedido" à ação que não conhece. */
    var oldScript = false

    /** O script recusa reservas (limite por hora). */
    var nameLimit = false

    /** Celulares que o dono tirou do ranking (removerJogador). */
    val removed = mutableSetOf<String>()

    /** O script novo recusa o lote inteiro com um motivo próprio (não é o "pedido" do script antigo). */
    var batchRefusal: String? = null

    /** O script de antes da 4d: não conhece "codigo" nem "vincular" (responde "pedido"). */
    var before4d = false

    /** Códigos de vínculo ativos: código → nome do jogador. */
    val codes = mutableMapOf<String, String>()

    /** Aparelhos que entraram num jogador pelo código: chave → nome. */
    val linked = mutableMapOf<String, String>()

    /** O script recusa vínculos (5 tentativas por hora). */
    var linkLimit = false

    private fun hasName(key: String) = key in names.values || key in linked

    override suspend fun post(url: String, json: String): Pair<Int, String> {
        val body = JSONObject(json)
        calls += body
        if (offline) throw IOException("sem rede")
        if (answerHtml) return 200 to "<html>Entrar no Google</html>"
        val key = body.optString("chave")
        if (key in removed) return 200 to """{"ok":false,"motivo":"removido"}"""
        val answer = when (body.getString("acao")) {
            "reservar" -> reserve(body, key)

            "enviar" -> if (hasName(key)) {
                """{"ok":true,"posicao":3,"total":27}"""
            } else {
                """{"ok":false,"motivo":"sem_nome"}"""
            }

            "enviarVarios" -> batch(body, key)

            "codigo" -> if (before4d) """{"ok":false,"motivo":"pedido"}""" else code(key)

            "vincular" -> if (before4d) """{"ok":false,"motivo":"pedido"}""" else link(body, key)

            else -> """{"ok":false,"motivo":"pedido"}"""
        }
        return 200 to answer
    }

    private fun reserve(body: JSONObject, key: String): String {
        if (nameLimit) return """{"ok":false,"motivo":"limite"}"""
        val name = body.getString("nome").trim()
        val owner = names[name.lowercase()]
        if (owner != null && owner != key) return """{"ok":false,"motivo":"nome_em_uso"}"""
        names.entries.removeAll { it.value == key }
        names[name.lowercase()] = key
        if (loseAnswer) throw IOException("resposta perdida")
        return """{"ok":true,"nome":"$name"}"""
    }

    private fun batch(body: JSONObject, key: String) = when {
        oldScript -> """{"ok":false,"motivo":"pedido"}"""

        batchRefusal != null -> """{"ok":false,"motivo":"$batchRefusal"}"""

        !hasName(key) -> """{"ok":false,"motivo":"sem_nome"}"""

        else -> {
            val placed = """{"ok":true,"posicao":3,"total":27}"""
            val results = List(body.getJSONArray("envios").length()) { placed }
            """{"ok":true,"resultados":[${results.joinToString(",")}]}"""
        }
    }

    private fun code(key: String): String {
        val name = names.entries.firstOrNull { it.value == key }?.key ?: linked[key]
            ?: return """{"ok":false,"motivo":"sem_nome"}"""
        codes["ABC234"] = name
        return """{"ok":true,"codigo":"ABC234","validoAte":"2026-11-04T12:10:00.000Z","validoPor":600}"""
    }

    private fun link(body: JSONObject, key: String): String {
        if (linkLimit) return """{"ok":false,"motivo":"limite"}"""
        val name = codes.remove(body.getString("codigo")) ?: return """{"ok":false,"motivo":"codigo_invalido"}"""
        names.entries.removeAll { it.value == key } // o nome próprio do aparelho fica livre
        linked[key] = name
        return """{"ok":true,"nome":"$name"}"""
    }

    fun sent(action: String) = calls.filter { it.getString("acao") == action }

    /** Cada pontuação que o script recebeu, sozinha ou num lote (as do lote vêm sem ação nem chave). */
    fun submitted(): List<JSONObject> = calls.flatMap { call ->
        when (call.getString("acao")) {
            "enviar" -> listOf(call)
            "enviarVarios" -> call.getJSONArray("envios").let { list -> List(list.length()) { list.getJSONObject(it) } }
            else -> emptyList()
        }
    }
}

/** A fila do Room em memória (o Room roda nas threads dele, fora do relógio do teste). */
class MemoryPendingDao : RankingPendingDao {
    private val rows = mutableListOf<RankingPendingEntity>()
    private var nextId = 1L

    override suspend fun insert(pending: RankingPendingEntity): Long = nextId++.also { rows += pending.copy(id = it) }

    override suspend fun all(): List<RankingPendingEntity> = rows.toList()

    override suspend fun delete(id: Long) {
        rows.removeAll { it.id == id }
    }
}

/** Só o título e o artista dos mapas; o resto do catálogo não é usado pelo ranking. */
class FakeCharts(ids: List<String>) : ChartRepository {
    private val charts = ids.associateWith {
        Chart(it, "Música $it", "Banda", null, null, null, "content://$it", ChartFormat.CHART, setOf())
    }

    override fun observeCharts() = flowOf(charts.values.toList())

    override suspend fun getChart(id: String) = charts[id]

    override suspend fun saveScanned(chart: Chart, scanId: Long) = Unit

    override suspend fun remove(ids: Collection<String>) = Unit

    override suspend fun removeNotSeenIn(scanId: Long) = Unit

    override suspend fun keepFolder(folderUri: String, scanId: Long) = Unit
}

class FakePlayers : PlayerStore {
    override val player = MutableStateFlow(Player(null, null))

    override suspend fun save(name: String, key: String) {
        player.value = Player(name, key)
    }

    override suspend fun saveKey(key: String) {
        player.value = player.value.copy(key = key)
    }

    override suspend fun forgetName() {
        player.value = player.value.copy(name = null)
    }

    override suspend fun forget() {
        player.value = Player(null, null)
    }
}

class FakeHistory(val records: MutableList<ScoreRecord> = mutableListOf()) : ScoreHistory {
    override suspend fun of(chartId: String, difficulty: Difficulty) =
        records.filter { it.chartId == chartId && it.difficulty == difficulty }

    override suspend fun all() = records.toList()
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RankingRepositoryTest {
    private val pending = MemoryPendingDao()
    private val charts = FakeCharts(listOf("m1", "m2"))
    private val server = FakeRankingServer()
    private val players = FakePlayers()
    private val history = FakeHistory()

    private fun record(
        id: Long,
        chartId: String = "m1",
        score: Long = 100,
        combo: Int = 50,
        clean: Long? = 30_000,
        table: RankingTable = RankingTable.PHONE,
    ) = ScoreRecord(id, chartId, Difficulty.HARD, score, 0.9f, combo, 40, 5, 2, 0, clean, table)

    private fun TestScope.repository(url: String = "https://script/exec") = AppsScriptRankingRepository(
        RankingApi(url, server),
        players,
        history,
        pending,
        charts,
        backgroundScope,
        keys = { "chave-1" },
    )

    @Test
    fun registerSavesNameAndKeyAndUploadsHistoryOnce() = runTest {
        history.records += listOf(record(1, "m1", score = 100), record(2, "m1", score = 120), record(3, "m2"))
        val ranking = repository()

        assertEquals(NameResult.Ok, ranking.register("Rodrigo"))
        runCurrent()
        assertEquals(Player("Rodrigo", "chave-1"), players.player.value)
        val uploads = server.submitted()
        assertEquals(2, uploads.size) // um por mapa+dificuldade, com o melhor de cada número
        assertEquals(120, uploads.first { it.getString("mapa") == "m1" }.getInt("pontos"))
        assertEquals("Música m1", uploads.first { it.getString("mapa") == "m1" }.getString("titulo"))

        assertEquals(NameResult.Ok, ranking.register("Digo")) // trocar o nome não reenvia o histórico
        runCurrent()
        assertEquals(2, server.submitted().size)
        assertEquals("chave-1", server.sent("reservar").last().getString("chave"))
    }

    /** A reserva chegou ao script, mas a resposta se perdeu: tentar de novo não pode dar "nome em uso" a si mesmo. */
    @Test
    fun aLostAnswerKeepsTheKeySoTheRetryWorks() = runTest {
        server.loseAnswer = true
        var nextKey = 0
        val ranking = AppsScriptRankingRepository(
            RankingApi("https://script/exec", server),
            players,
            history,
            pending,
            charts,
            backgroundScope,
            keys = { "chave-${++nextKey}" },
        )
        assertEquals(NameResult.Offline, ranking.register("Rodrigo"))

        server.loseAnswer = false
        assertEquals(NameResult.Ok, ranking.register("Rodrigo"))
        assertEquals(Player("Rodrigo", "chave-1"), players.player.value)
    }

    @Test
    fun aTakenNameIsNotSaved() = runTest {
        server.names["rodrigo"] = "outra-chave"

        assertEquals(NameResult.Taken, repository().register("Rodrigo"))
        assertNull(players.player.value.name)
    }

    @Test
    fun anInvalidNameIsCaughtWithoutCallingTheServer() = runTest {
        assertEquals(NameResult.Invalid, repository().register("ab"))
        assertEquals(NameResult.Invalid, repository().register("nome-com-mais-de-vinte"))
        assertTrue(server.calls.isEmpty())
    }

    @Test
    fun offlineRegistrationSaysSo() = runTest {
        server.offline = true

        assertEquals(NameResult.Offline, repository().register("Rodrigo"))
    }

    @Test
    fun onlyABestIsSubmitted() = runTest {
        players.player.value = Player("Rodrigo", "chave-1")
        server.names["rodrigo"] = "chave-1"
        history.records += record(1, score = 100, combo = 50)
        val ranking = repository()

        val better = record(2, score = 90, combo = 60).also { history.records += it }
        ranking.submitIfBest(better, "Música", "Banda")
        runCurrent()
        assertEquals(SubmitStatus.Placed(3, 27), ranking.statusFor(2).value)

        val worse = record(3, score = 80, combo = 40).also { history.records += it }
        ranking.submitIfBest(worse, "Música", "Banda")
        runCurrent()
        assertEquals(SubmitStatus.NotBest, ranking.statusFor(3).value)
        assertEquals(1, server.submitted().size)
    }

    /** Título e artista vêm do mapa (qualquer tamanho): cortados, a pontuação cabe no pedido do script. */
    @Test
    fun aHugeTitleIsCutBeforeSending() = runTest {
        players.player.value = Player("Rodrigo", "chave-1")
        server.names["rodrigo"] = "chave-1"
        val ranking = repository()

        val record = record(1, score = 100, combo = 50).also { history.records += it }
        ranking.submitIfBest(record, "M".repeat(5_000), "B".repeat(5_000))
        runCurrent()

        val sent = server.submitted().single()
        assertEquals(200, sent.getString("titulo").length)
        assertEquals(200, sent.getString("artista").length)
    }

    /** A fila sai ao terminar qualquer partida (spec §4), não só uma que seja recorde. */
    @Test
    fun aMatchThatIsNotABestStillSendsTheQueue() = runTest {
        players.player.value = Player("Rodrigo", "chave-1")
        server.names["rodrigo"] = "chave-1"
        history.records += record(1, score = 100)
        val ranking = repository()
        server.offline = true
        ranking.submitIfBest(record(2, score = 200).also { history.records += it }, "Música", "Banda")
        runCurrent()
        assertEquals(1, pending.all().size)

        server.offline = false
        ranking.submitIfBest(record(3, score = 50).also { history.records += it }, "Música", "Banda")
        runCurrent()

        assertEquals(SubmitStatus.NotBest, ranking.statusFor(3).value)
        assertTrue(pending.all().isEmpty())
    }

    @Test
    fun withoutANameItIsQueuedAndSentAfterRegistering() = runTest {
        val ranking = repository()
        val match = record(1).also { history.records += it }

        ranking.submitIfBest(match, "Música", "Banda")
        runCurrent()
        assertEquals(SubmitStatus.NoName, ranking.statusFor(1).value)
        assertTrue(server.submitted().isEmpty())

        ranking.register("Rodrigo")
        runCurrent()
        assertTrue(server.submitted().isNotEmpty())
        assertTrue(pending.all().isEmpty())
    }

    @Test
    fun aQueuedSubmissionSurvivesAndIsSentLater() = runTest {
        players.player.value = Player("Rodrigo", "chave-1")
        server.names["rodrigo"] = "chave-1"
        server.offline = true
        val match = record(1).also { history.records += it }
        repository().submitIfBest(match, "Música", "Banda")
        runCurrent()
        assertEquals(1, pending.all().size)

        server.offline = false
        repository().flush() // o app foi fechado e aberto de novo: outro repositório, mesma fila guardada

        assertEquals(2, server.submitted().size) // a tentativa sem rede e a que chegou
        assertTrue(pending.all().isEmpty())
    }

    @Test
    fun aNonJsonAnswerKeepsTheSubmissionQueued() = runTest {
        players.player.value = Player("Rodrigo", "chave-1")
        server.answerHtml = true
        val ranking = repository()
        val match = record(1).also { history.records += it }

        ranking.submitIfBest(match, "Música", "Banda")
        runCurrent()

        assertEquals(SubmitStatus.Queued, ranking.statusFor(1).value)
        assertEquals(1, pending.all().size)
    }

    @Test
    fun withoutUrlNothingIsCalled() = runTest {
        val ranking = repository(url = "")

        assertFalse(ranking.isConfigured)
        ranking.submitIfBest(record(1).also { history.records += it }, "Música", "Banda")
        runCurrent()
        assertEquals(NameResult.Offline, ranking.register("Rodrigo"))
        assertTrue(server.calls.isEmpty())
    }

    @Test
    fun anOldMatchGoesWithoutCleanTime() = runTest {
        history.records += record(1, clean = null)

        repository().register("Rodrigo")
        runCurrent()

        assertTrue(server.submitted().single().isNull("semErrar"))
    }

    @Test
    fun theHistoryGoesInOneRequest() = runTest {
        history.records += listOf(record(1, "m1"), record(2, "m2"))

        repository().register("Rodrigo")
        runCurrent()

        assertEquals(1, server.sent("enviarVarios").size)
        assertEquals(2, server.submitted().size)
        assertTrue(server.sent("enviar").isEmpty())
        assertTrue(pending.all().isEmpty())
    }

    /** O app novo com o script antigo (antes de republicar): manda uma por vez, sem perder nada. */
    @Test
    fun anOldScriptStillGetsEverythingOneByOne() = runTest {
        server.oldScript = true
        history.records += listOf(record(1, "m1"), record(2, "m2"))

        repository().register("Rodrigo")
        runCurrent()

        assertEquals(2, server.sent("enviar").size)
        assertTrue(pending.all().isEmpty())
    }

    /** O dono apagou o jogador da planilha: o celular reserva o mesmo nome de novo e envia. */
    @Test
    fun aPlayerDeletedFromTheSheetIsRegisteredAgain() = runTest {
        players.player.value = Player("Rodrigo", "chave-1") // o servidor não conhece mais ninguém
        val ranking = repository()

        ranking.submitIfBest(record(1).also { history.records += it }, "Música", "Banda")
        runCurrent()

        assertEquals("Rodrigo", server.sent("reservar").single().getString("nome"))
        assertEquals(SubmitStatus.Placed(3, 27), ranking.statusFor(1).value)
        assertTrue(pending.all().isEmpty())
    }

    /** Apagado e o nome já é de outra pessoa: pede um nome novo, sem perder a partida. */
    @Test
    fun aDeletedPlayerWhoseNameWasTakenIsAskedAgain() = runTest {
        players.player.value = Player("Rodrigo", "chave-1")
        server.names["rodrigo"] = "outra-chave"
        val ranking = repository()

        ranking.submitIfBest(record(1).also { history.records += it }, "Música", "Banda")
        runCurrent()

        assertNull(players.player.value.name)
        assertEquals("chave-1", players.player.value.key)
        assertEquals(SubmitStatus.NoName, ranking.statusFor(1).value)
        assertEquals(1, pending.all().size)
    }

    /** O dono tirou o jogador do ranking (pedido de exclusão): o celular esquece nome, chave e fila. */
    @Test
    fun aPlayerRemovedByTheOwnerLeavesTheRanking() = runTest {
        players.player.value = Player("Rodrigo", "chave-1")
        server.names["rodrigo"] = "chave-1"
        server.removed += "chave-1"
        val ranking = repository()

        ranking.submitIfBest(record(1).also { history.records += it }, "Música", "Banda")
        runCurrent()

        assertEquals(Player(null, null), players.player.value)
        assertTrue(pending.all().isEmpty())
        assertTrue(server.sent("reservar").isEmpty()) // não volta sozinho
        assertEquals(SubmitStatus.NoName, ranking.statusFor(1).value)
    }

    /** Só o "pedido" do script antigo faz mandar uma por vez; uma recusa do lote pelo script novo espera. */
    @Test
    fun aBatchRefusedByTheNewScriptKeepsTheQueue() = runTest {
        players.player.value = Player("Rodrigo", "chave-1")
        server.names["rodrigo"] = "chave-1"
        server.batchRefusal = "lote_invalido"
        val ranking = repository()

        ranking.submitIfBest(record(1).also { history.records += it }, "Música", "Banda")
        runCurrent()

        assertTrue(server.sent("enviar").isEmpty())
        assertEquals(1, pending.all().size)
    }

    @Test
    fun tooManyNamesInAnHourSaysSo() = runTest {
        server.nameLimit = true

        assertEquals(NameResult.Limit, repository().register("Rodrigo"))
    }

    @Test
    fun notNowLastsOnlyForThisOpening() = runTest {
        val ranking = repository()
        ranking.askLater()
        assertTrue(ranking.askedLater.value)

        assertFalse(repository().askedLater.value)
        assertEquals(Player(null, null), players.player.first())
    }

    /** "É o melhor?" só dentro da tabela da partida: o recorde do celular não segura a primeira do PC. */
    @Test
    fun theBestIsPerTable() = runTest {
        players.player.value = Player("Rodrigo", "chave-1")
        server.names["rodrigo"] = "chave-1"
        history.records += record(1, score = 1_000, combo = 500)
        val ranking = repository()

        val pc = record(2, score = 100, combo = 10, clean = 1, table = RankingTable.PC_STRUM)
        ranking.submitIfBest(pc.also { history.records += it }, "Música", "Banda")
        runCurrent()
        val phone = record(3, score = 500, combo = 10, clean = 1)
        ranking.submitIfBest(phone.also { history.records += it }, "Música", "Banda")
        runCurrent()

        assertEquals(SubmitStatus.Placed(3, 27), ranking.statusFor(2).value)
        assertEquals(SubmitStatus.NotBest, ranking.statusFor(3).value)
        assertEquals(listOf("pc_palheta"), server.submitted().map { it.getString("tabela") })
    }

    @Test
    fun theHistoryGoesWithTheTableOfEachMatch() = runTest {
        history.records += listOf(
            record(1, score = 100),
            record(2, score = 300, table = RankingTable.PC_STRUM),
            record(3, score = 200, table = RankingTable.PC_STRUM),
        )
        val ranking = repository()

        ranking.register("Rodrigo")
        runCurrent()

        val sent = server.submitted().associate { it.getString("tabela") to it.getInt("pontos") }
        assertEquals(mapOf("celular" to 100, "pc_palheta" to 300), sent)
    }

    /** O código vem do script; sem nome, nem pergunta. */
    @Test
    fun aLinkCodeIsAskedForThePlayerOfThisDevice() = runTest {
        val ranking = repository()
        assertEquals(LinkResult.NoName, ranking.linkCode())
        assertTrue(server.calls.isEmpty())

        players.player.value = Player("Rodrigo", "chave-1")
        server.names["rodrigo"] = "chave-1"

        assertEquals(LinkResult.Code("ABC234", 600), ranking.linkCode())
        assertEquals("chave-1", server.sent("codigo").single().getString("chave"))
    }

    /** No outro aparelho: o código (minúsculas e traço valem) traz o nome, que fica salvo, e o histórico vai junto. */
    @Test
    fun linkingSavesTheNameAndSendsTheHistory() = runTest {
        server.codes["ABC234"] = "Rodrigo"
        history.records += record(1, score = 100, table = RankingTable.PC_NO_STRUM)
        val ranking = repository()

        assertEquals(LinkResult.Linked("Rodrigo"), ranking.link(" abc-234 "))
        runCurrent()

        assertEquals("ABC234", server.sent("vincular").single().getString("codigo"))
        assertEquals("chave-1", server.sent("vincular").single().getString("chave"))
        assertEquals(Player("Rodrigo", "chave-1"), players.player.value)
        assertEquals(listOf("pc_sem_palheta"), server.submitted().map { it.getString("tabela") })
        assertTrue(pending.all().isEmpty())
    }

    /** Já com nome próprio: o script tira as pontuações dele, então o histórico vai de novo, agora no outro nome. */
    @Test
    fun linkingADeviceThatHadANameSendsTheHistoryAgain() = runTest {
        players.player.value = Player("Digo", "chave-9")
        server.names["digo"] = "chave-9"
        server.codes["ABC234"] = "Rodrigo"
        history.records += record(1, score = 100)
        val ranking = repository()

        assertEquals(LinkResult.Linked("Rodrigo"), ranking.link("ABC234"))
        runCurrent()

        assertEquals(Player("Rodrigo", "chave-9"), players.player.value)
        assertEquals(1, server.submitted().size)
    }

    @Test
    fun aWrongCodeKeepsEverything() = runTest {
        players.player.value = Player("Digo", "chave-9")
        val ranking = repository()

        assertEquals(LinkResult.InvalidCode, ranking.link("ZZZ999"))
        assertEquals(LinkResult.InvalidCode, ranking.link("AB")) // curto: nem pergunta
        assertEquals(1, server.sent("vincular").size)
        assertEquals(Player("Digo", "chave-9"), players.player.value)
        assertTrue(server.submitted().isEmpty())
    }

    @Test
    fun linkErrorsSaySo() = runTest {
        val ranking = repository()

        server.linkLimit = true
        assertEquals(LinkResult.Limit, ranking.link("ABC234"))
        server.linkLimit = false
        server.offline = true
        assertEquals(LinkResult.Offline, ranking.link("ABC234"))
        assertNull(players.player.value.name)
    }

    /** O app novo com o script de antes da 4d (ainda não republicado): avisa, não trava. */
    @Test
    fun theOldScriptMeansTheRankingIsOutdated() = runTest {
        players.player.value = Player("Rodrigo", "chave-1")
        server.names["rodrigo"] = "chave-1"
        server.before4d = true
        val ranking = repository()

        assertEquals(LinkResult.Outdated, ranking.linkCode())
        assertEquals(LinkResult.Outdated, ranking.link("ABC234"))
        assertEquals(Player("Rodrigo", "chave-1"), players.player.value)
    }

    @Test
    fun aRemovedDeviceLeavesWhenLinking() = runTest {
        players.player.value = Player("Rodrigo", "chave-1")
        server.removed += "chave-1"
        val ranking = repository()

        assertEquals(LinkResult.Removed, ranking.link("ABC234"))
        assertEquals(Player(null, null), players.player.value)
    }

    @Test
    fun onThePhoneEveryTableStaysAsSaved() {
        assertEquals(RankingTable.PHONE, RankingTable.PHONE.forThisDevice())
        assertEquals(RankingTable.PC_STRUM, RankingTable.PC_STRUM.forThisDevice())
    }

    /**
     * Decisão 5: no PC, as partidas de antes da 4d (que a migração marcou "celular") contam com palheta, o padrão de
     * fábrica.
     */
    @Test
    fun onThePcTheOldPhoneMarkMeansWithStrum() {
        assertEquals(RankingTable.PC_STRUM, RankingTable.PHONE.forDevice(pc = true))
        assertEquals(RankingTable.PC_NO_STRUM, RankingTable.PC_NO_STRUM.forDevice(pc = true))
        assertEquals(RankingTable.PC_STRUM, RankingTable.PC_STRUM.forDevice(pc = true))
        assertEquals(RankingTable.PHONE, RankingTable.PHONE.forDevice(pc = false))
    }
}
