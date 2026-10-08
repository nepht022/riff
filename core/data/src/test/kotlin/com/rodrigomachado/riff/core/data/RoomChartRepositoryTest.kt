package com.rodrigomachado.riff.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rodrigomachado.riff.core.database.RiffDatabase
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomChartRepositoryTest {
    private lateinit var db: RiffDatabase
    private lateinit var repository: RoomChartRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RiffDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomChartRepository(db.chartDao())
    }

    @After
    fun tearDown() = db.close()

    private val chart = Chart(
        id = "sha1",
        title = "Through the Fire and Flames",
        artist = "DragonForce",
        album = "Inhuman Rampage",
        charter = "Alguém",
        durationMs = 441_000,
        folderUri = "content://tree/primary%3ACharts/document/primary%3ACharts%2FTTFAF",
        format = ChartFormat.MIDI,
        difficulties = setOf(Difficulty.HARD, Difficulty.EXPERT),
    )

    @Test
    fun savedChartComesBackIdentical() = runTest {
        repository.saveScanned(chart, scanId = 1)
        assertEquals(listOf(chart), repository.observeCharts().first())
    }

    @Test
    fun removeNotSeenInDropsChartsFromOlderScans() = runTest {
        repository.saveScanned(chart, scanId = 1)
        repository.saveScanned(chart.copy(id = "outro", title = "Outro"), scanId = 2)

        repository.removeNotSeenIn(scanId = 2)

        assertEquals(listOf("outro"), repository.observeCharts().first().map { it.id })
    }
}
