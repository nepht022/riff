package com.rodrigomachado.riff.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rodrigomachado.riff.core.database.ChartEntity
import com.rodrigomachado.riff.core.database.RiffDatabase
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.RankingTable
import com.rodrigomachado.riff.core.model.ScoreRecord
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
}
