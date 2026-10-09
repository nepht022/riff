package com.rodrigomachado.riff.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChartOffsetDaoTest {
    private lateinit var db: RiffDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RiffDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun chart(id: String, scanId: Long = 1) =
        ChartEntity(id, "Música $id", "Banda", null, null, null, "content://$id", "CHART", scanId)

    @Test
    fun savesAndReadsTheOffsetOfAChart() = runTest {
        db.chartDao().replace(chart("a"), emptyList())

        db.chartOffsetDao().upsert(ChartOffsetEntity("a", -30))
        db.chartOffsetDao().upsert(ChartOffsetEntity("a", 20))

        assertEquals(20, db.chartOffsetDao().get("a"))
        assertNull(db.chartOffsetDao().get("b"))
    }

    @Test
    fun removingAChartRemovesItsOffset() = runTest {
        db.chartDao().replace(chart("a", scanId = 1), emptyList())
        db.chartOffsetDao().upsert(ChartOffsetEntity("a", 20))

        db.chartDao().deleteNotSeenIn(2)

        assertNull(db.chartOffsetDao().get("a"))
    }

    @Test
    fun reimportingAChartKeepsItsOffset() = runTest {
        db.chartDao().replace(chart("a", scanId = 1), emptyList())
        db.chartOffsetDao().upsert(ChartOffsetEntity("a", 20))

        db.chartDao().replace(chart("a", scanId = 2), emptyList())
        db.chartDao().deleteNotSeenIn(2)

        assertEquals(20, db.chartOffsetDao().get("a"))
    }
}
