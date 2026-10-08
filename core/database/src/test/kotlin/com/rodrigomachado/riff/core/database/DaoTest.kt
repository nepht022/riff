package com.rodrigomachado.riff.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
class DaoTest {
    private lateinit var db: RiffDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RiffDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun chart(id: String, title: String = "Música $id", artist: String = "Banda", scanId: Long = 1) =
        ChartEntity(id, title, artist, null, null, null, "content://$id", "CHART", scanId)

    @Test
    fun replaceStoresChartWithItsDifficulties() = runTest {
        db.chartDao().replace(
            chart("a"),
            listOf(ChartDifficultyEntity("a", "EASY"), ChartDifficultyEntity("a", "EXPERT")),
        )

        val stored = db.chartDao().observeAll().first().single()
        assertEquals("a", stored.chart.id)
        assertEquals(setOf("EASY", "EXPERT"), stored.difficulties.map { it.difficulty }.toSet())
    }

    @Test
    fun replacingAgainUpdatesWithoutDuplicating() = runTest {
        db.chartDao().replace(chart("a", title = "Antigo"), listOf(ChartDifficultyEntity("a", "EASY")))
        db.chartDao().replace(chart("a", title = "Novo"), listOf(ChartDifficultyEntity("a", "HARD")))

        val stored = db.chartDao().observeAll().first().single()
        assertEquals("Novo", stored.chart.title)
        assertEquals(listOf("HARD"), stored.difficulties.map { it.difficulty })
    }

    @Test
    fun listIsSortedByArtistThenTitleIgnoringCase() = runTest {
        db.chartDao().replace(chart("1", title = "b", artist = "zeca"), emptyList())
        db.chartDao().replace(chart("2", title = "B", artist = "Ana"), emptyList())
        db.chartDao().replace(chart("3", title = "a", artist = "ana"), emptyList())

        assertEquals(listOf("3", "2", "1"), db.chartDao().observeAll().first().map { it.chart.id })
    }

    @Test
    fun deleteNotSeenInRemovesOldChartsAndTheirDependents() = runTest {
        db.chartDao().replace(chart("velho", scanId = 1), listOf(ChartDifficultyEntity("velho", "EASY")))
        db.chartDao().replace(chart("novo", scanId = 2), emptyList())
        db.trackMatchDao().upsert(TrackMatchEntity("spotify:track:1", "velho", 1f, false, 0))
        db.scoreDao().insert(score("velho", "EASY", 100))

        db.chartDao().deleteNotSeenIn(scanId = 2)

        assertEquals(listOf("novo"), db.chartDao().observeAll().first().map { it.chart.id })
        assertNull(db.trackMatchDao().get("spotify:track:1"))
        assertNull(db.scoreDao().best("velho", "EASY"))
    }

    @Test
    fun handlesLibrariesLargerThanTheSqliteVariableLimit() = runTest {
        repeat(1_500) { db.chartDao().replace(chart("old-$it", scanId = 1), emptyList()) }
        db.chartDao().replace(chart("kept", scanId = 2), emptyList())

        db.chartDao().deleteNotSeenIn(scanId = 2)

        assertEquals(listOf("kept"), db.chartDao().observeAll().first().map { it.chart.id })
    }

    @Test
    fun trackMatchUpsertReplacesPreviousMatch() = runTest {
        db.chartDao().replace(chart("a"), emptyList())
        db.chartDao().replace(chart("b"), emptyList())
        db.trackMatchDao().upsert(TrackMatchEntity("spotify:track:1", "a", 0.8f, false, 0))
        db.trackMatchDao().upsert(TrackMatchEntity("spotify:track:1", "b", 1f, true, -40))

        assertEquals(TrackMatchEntity("spotify:track:1", "b", 1f, true, -40), db.trackMatchDao().get("spotify:track:1"))
    }

    @Test
    fun bestScoreIsTheHighestForThatDifficulty() = runTest {
        db.chartDao().replace(chart("a"), emptyList())
        db.scoreDao().insert(score("a", "EXPERT", 500))
        db.scoreDao().insert(score("a", "EXPERT", 900))
        db.scoreDao().insert(score("a", "EASY", 5_000))

        assertEquals(900L, db.scoreDao().best("a", "EXPERT")?.score)
    }

    private fun score(chartId: String, difficulty: String, value: Long) = ScoreEntity(
        chartId = chartId,
        difficulty = difficulty,
        score = value,
        accuracy = 0.5f,
        maxCombo = 1,
        perfect = 0,
        good = 0,
        miss = 0,
        playedAtEpochMs = 0,
    )
}
