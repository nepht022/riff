package com.rodrigomachado.riff.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rodrigomachado.riff.core.database.ChartEntity
import com.rodrigomachado.riff.core.database.RiffDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomEncoreDownloadsTest {
    private lateinit var db: RiffDatabase
    private lateinit var downloads: RoomEncoreDownloads

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RiffDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        downloads = RoomEncoreDownloads(db.encoreChartDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aDownloadedChartStaysMarkedAfterTheLibraryIsRescanned() = runTest {
        val chart = ChartEntity("a", "M", "B", null, null, null, "content://a#sng", "CHART", 1)
        db.chartDao().replace(chart, emptyList())
        downloads.remember("a", "a07d209baccd2d58042d81b869a0077e")

        db.chartDao().replace(chart.copy(lastSeenScanId = 2), emptyList()) // Reimportar regrava a linha do mapa

        assertEquals(setOf("a07d209baccd2d58042d81b869a0077e"), downloads.md5s.first())
        assertEquals("a", downloads.chartIdOf("a07d209baccd2d58042d81b869a0077e"))
    }

    @Test
    fun removingTheChartForgetsTheDownload() = runTest {
        db.chartDao().replace(ChartEntity("a", "M", "B", null, null, null, "u", "CHART", 1), emptyList())
        downloads.remember("a", "m5")

        db.chartDao().deleteNotSeenIn(2)

        assertEquals(emptySet<String>(), downloads.md5s.first())
    }
}
