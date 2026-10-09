package com.rodrigomachado.riff.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rodrigomachado.riff.core.database.ChartEntity
import com.rodrigomachado.riff.core.database.RiffDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomChartOffsetRepositoryTest {
    private lateinit var db: RiffDatabase
    private lateinit var offsets: RoomChartOffsetRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RiffDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        offsets = RoomChartOffsetRepository(db.chartOffsetDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aChartWithoutAdjustmentHasZero() = runTest {
        assertEquals(0, offsets.get("a"))
    }

    @Test
    fun theAdjustmentIsSavedWithinTheLimits() = runTest {
        db.chartDao().replace(ChartEntity("a", "M", "B", null, null, null, "content://a", "CHART", 1), emptyList())

        offsets.save("a", 30)
        assertEquals(30, offsets.get("a"))

        offsets.save("a", 900)
        assertEquals(MAX_SYNC_OFFSET_MS, offsets.get("a"))
    }
}
