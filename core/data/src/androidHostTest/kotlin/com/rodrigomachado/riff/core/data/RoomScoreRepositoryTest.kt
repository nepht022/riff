package com.rodrigomachado.riff.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rodrigomachado.riff.core.database.ChartEntity
import com.rodrigomachado.riff.core.database.RiffDatabase
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.RankingTable
import com.rodrigomachado.riff.core.model.ScoreRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomScoreRepositoryTest {
    private lateinit var db: RiffDatabase
    private lateinit var scores: RoomScoreRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RiffDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scores = RoomScoreRepository(db.scoreDao())
    }

    @After
    fun tearDown() = db.close()

    private fun record(score: Long) = ScoreRecord(0, "a", Difficulty.EXPERT, score, 0.875f, 12, 7, 2, 1, 1_000)

    @Test
    fun savedRecordComesBackWithItsNewId() = runTest {
        db.chartDao().replace(ChartEntity("a", "t", "b", null, null, null, "u", "CHART", 1), emptyList())

        val id = scores.save(record(1_200))

        assertEquals(record(1_200).copy(id = id), scores.get(id))
        assertNull(scores.get(id + 1))
    }

    @Test
    fun bestIsTheHighestScoreOfThatDifficulty() = runTest {
        db.chartDao().replace(ChartEntity("a", "t", "b", null, null, null, "u", "CHART", 1), emptyList())
        scores.save(record(500))
        val bestId = scores.save(record(900))

        assertEquals(bestId, scores.best("a", Difficulty.EXPERT)?.id)
        assertNull(scores.best("a", Difficulty.EASY))
    }

    /** A partida guarda a tabela em que conta no ranking. */
    @Test
    fun theRankingTableIsSaved() = runTest {
        db.chartDao().replace(ChartEntity("a", "t", "b", null, null, null, "u", "CHART", 1), emptyList())

        val id = scores.save(record(1_200).copy(rankingTable = RankingTable.PC_STRUM))

        assertEquals(RankingTable.PC_STRUM, scores.get(id)?.rankingTable)
    }

    /**
     * O "antes: N" do Resultado: o maior recorde da mesma dificuldade gravado antes da partida (nem ela, nem as que
     * vieram depois, nem a outra dificuldade); a primeira partida não tem.
     */
    @Test
    fun bestBeforeIsTheHighestScoreSavedBeforeThatMatch() = runTest {
        charts("a")
        val first = scores.save(record(500))
        scores.save(record(700))
        scores.save(record(9_000).copy(difficulty = Difficulty.EASY))
        val current = scores.save(record(800))
        scores.save(record(1_000))

        assertNull(scores.bestBefore("a", Difficulty.EXPERT, first))
        assertEquals(700L, scores.bestBefore("a", Difficulty.EXPERT, current)?.score)
    }

    /** "Continuar" da tela inicial: a última partida jogada, de qualquer mapa; sem partidas, nada. */
    @Test
    fun lastIsTheMostRecentlyPlayedMatch() = runTest {
        charts("a", "b", "c")
        assertNull(scores.last())
        scores.save(record(900).copy(playedAtEpochMs = 5_000))
        val lastId = scores.save(record(100).copy(chartId = "b", playedAtEpochMs = 9_000))
        scores.save(record(700).copy(playedAtEpochMs = 2_000))

        assertEquals(lastId, scores.last()?.id)
    }

    /**
     * "Últimos recordes": o recorde de hoje de cada mapa e dificuldade, do mais recente ao mais antigo; uma partida
     * que não bateu o recorde não entra (nem tira o recorde dela da lista).
     */
    @Test
    fun recentBestsAreTheCurrentBestsNewestFirst() = runTest {
        charts("a", "b", "c")
        val oldBestA = scores.save(record(900).copy(playedAtEpochMs = 1_000))
        scores.save(record(300).copy(playedAtEpochMs = 8_000)) // não bateu
        val bestB = scores.save(record(500).copy(chartId = "b", playedAtEpochMs = 4_000))
        val bestAEasy = scores.save(record(200).copy(difficulty = Difficulty.EASY, playedAtEpochMs = 6_000))
        val bestC = scores.save(record(50).copy(chartId = "c", playedAtEpochMs = 7_000))

        assertEquals(listOf(bestC, bestAEasy, bestB), scores.recentBests(3).map { it.id })
        assertEquals(listOf(bestC, bestAEasy, bestB, oldBestA), scores.recentBests(10).map { it.id })
    }

    /**
     * A Biblioteca: o maior recorde de cada mapa, em qualquer dificuldade (empate: o mais antigo, como no [best]);
     * mapa sem partidas fica de fora. Uma partida nova aparece sem reabrir a lista.
     */
    @Test
    fun chartBestsAreTheHighestScoreOfEachChartInAnyDifficulty() = runTest {
        charts("a", "b", "c")
        scores.save(record(300))
        val bestA = scores.save(record(900).copy(difficulty = Difficulty.EASY))
        scores.save(record(900).copy(difficulty = Difficulty.HARD)) // empate: fica o mais antigo
        val bestB = scores.save(record(50).copy(chartId = "b"))

        val bests = scores.observeChartBests().first().associate { it.chartId to it.id }
        assertEquals(mapOf("a" to bestA, "b" to bestB), bests)
        val newBestB = scores.save(record(70).copy(chartId = "b", difficulty = Difficulty.MEDIUM))
        assertEquals(newBestB, scores.observeChartBests().first().single { it.chartId == "b" }.id)
    }

    private suspend fun charts(vararg ids: String) = ids.forEach {
        db.chartDao().replace(ChartEntity(it, "t", "b", null, null, null, "u", "CHART", 1), emptyList())
    }
}
