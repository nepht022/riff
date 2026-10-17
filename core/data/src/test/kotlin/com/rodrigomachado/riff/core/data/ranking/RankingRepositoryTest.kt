package com.rodrigomachado.riff.core.data.ranking

import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.database.RankingPendingDao
import com.rodrigomachado.riff.core.database.RankingPendingEntity
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
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

    override suspend fun post(url: String, json: String): Pair<Int, String> {
        val body = JSONObject(json)
        calls += body
        if (offline) throw IOException("sem rede")
        if (answerHtml) return 200 to "<html>Entrar no Google</html>"
        val key = body.optString("chave")
        val answer = when (body.getString("acao")) {
            "reservar" -> {
                val name = body.getString("nome").trim()
                val owner = names[name.lowercase()]
                if (owner != null && owner != key) {
                    """{"ok":false,"motivo":"nome_em_uso"}"""
                } else {
                    names.entries.removeAll { it.value == key }
                    names[name.lowercase()] = key
                    """{"ok":true,"nome":"$name"}"""
                }
            }

            "enviar" -> if (key in names.values) {
                """{"ok":true,"posicao":3,"total":27}"""
            } else {
                """{"ok":false,"motivo":"sem_nome"}"""
            }

            else -> """{"ok":false,"motivo":"pedido"}"""
        }
        return 200 to answer
    }

    fun sent(action: String) = calls.filter { it.getString("acao") == action }
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

    private fun record(id: Long, chartId: String = "m1", score: Long = 100, combo: Int = 50, clean: Long? = 30_000) =
        ScoreRecord(id, chartId, Difficulty.HARD, score, 0.9f, combo, 40, 5, 2, 0, clean)

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
        val uploads = server.sent("enviar")
        assertEquals(2, uploads.size) // um por mapa+dificuldade, com o melhor de cada número
        assertEquals(120, uploads.first { it.getString("mapa") == "m1" }.getInt("pontos"))
        assertEquals("Música m1", uploads.first { it.getString("mapa") == "m1" }.getString("titulo"))

        assertEquals(NameResult.Ok, ranking.register("Digo")) // trocar o nome não reenvia o histórico
        runCurrent()
        assertEquals(2, server.sent("enviar").size)
        assertEquals("chave-1", server.sent("reservar").last().getString("chave"))
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
        assertEquals(1, server.sent("enviar").size)
    }

    @Test
    fun withoutANameItIsQueuedAndSentAfterRegistering() = runTest {
        val ranking = repository()
        val match = record(1).also { history.records += it }

        ranking.submitIfBest(match, "Música", "Banda")
        runCurrent()
        assertEquals(SubmitStatus.NoName, ranking.statusFor(1).value)
        assertTrue(server.sent("enviar").isEmpty())

        ranking.register("Rodrigo")
        runCurrent()
        assertTrue(server.sent("enviar").isNotEmpty())
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

        assertEquals(2, server.sent("enviar").size) // a tentativa sem rede e a que chegou
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

        assertTrue(server.sent("enviar").single().isNull("semErrar"))
    }

    @Test
    fun notNowLastsOnlyForThisOpening() = runTest {
        val ranking = repository()
        ranking.askLater()
        assertTrue(ranking.askedLater.value)

        assertFalse(repository().askedLater.value)
        assertEquals(Player(null, null), players.player.first())
    }
}
