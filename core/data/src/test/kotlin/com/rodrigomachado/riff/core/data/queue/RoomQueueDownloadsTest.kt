package com.rodrigomachado.riff.core.data.queue

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rodrigomachado.riff.core.database.ChartEntity
import com.rodrigomachado.riff.core.database.RiffDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomQueueDownloadsTest {
    private lateinit var db: RiffDatabase
    private lateinit var downloads: RoomQueueDownloads

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RiffDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        downloads = RoomQueueDownloads(db.queueDownloadDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun chart(id: String) =
        db.chartDao().replace(ChartEntity(id, "M", "B", null, null, null, "content://$id", "CHART", 1), emptyList())

    @Test
    fun addedMapsAreUnplayedUntilMarked() = runTest {
        chart("a")
        chart("b")
        downloads.add("a", "spotify:track:a", at = 10)
        downloads.add("b", "spotify:track:b", at = 20)

        downloads.markPlayed("a")

        assertEquals(listOf(QueueDownload("b", "spotify:track:b", 20, played = false)), downloads.unplayed())
    }

    @Test
    fun removedMapsDisappear() = runTest {
        chart("a")
        downloads.add("a", "spotify:track:a", at = 10)

        downloads.remove(listOf("a"))

        assertTrue(downloads.unplayed().isEmpty())
    }

    @Test
    fun aMapIsFoundByItsTrack() = runTest {
        chart("a")
        downloads.add("a", "spotify:track:a", at = 10)

        assertEquals("a", downloads.forTrack("spotify:track:a")?.chartId)
        assertNull(downloads.forTrack("spotify:track:z"))
    }

    /** Apagado da biblioteca (por qualquer caminho), o registro da fila vai junto. */
    @Test
    fun deletingTheChartRemovesItsQueueRow() = runTest {
        chart("a")
        downloads.add("a", "spotify:track:a", at = 10)

        db.chartDao().deleteAll(listOf("a"))

        assertTrue(downloads.unplayed().isEmpty())
    }
}
