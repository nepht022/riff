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
class SeparationQueueDaoTest {
    private lateinit var db: RiffDatabase
    private val dao get() = db.separationQueueDao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RiffDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun charts(vararg ids: String) = ids.forEach {
        db.chartDao().replace(
            ChartEntity(it, "Música $it", "Banda", null, null, null, "content://$it", "CHART", 1),
            emptyList(),
        )
    }

    private fun items(vararg ids: String) = ids.map { it to "Música $it" }

    @Test
    fun theQueueComesInArrivalOrder() = runTest {
        charts("a", "b", "c")
        dao.append(items("c", "a"))
        dao.append(items("b"))

        assertEquals(listOf("c", "a", "b"), dao.observe().first().map { it.chartId })
        assertEquals("c", dao.first()?.chartId)
    }

    @Test
    fun aSongAlreadyInTheQueueKeepsItsPlace() = runTest {
        charts("a", "b")
        dao.append(items("a", "b"))

        dao.append(items("a"))

        assertEquals(listOf("a", "b"), dao.observe().first().map { it.chartId })
    }

    @Test
    fun deletingTheChartTakesItOutOfTheQueue() = runTest {
        charts("a", "b")
        dao.append(items("a", "b"))

        db.chartDao().deleteAll(listOf("a"))

        assertEquals(listOf("b"), dao.observe().first().map { it.chartId })
    }

    @Test
    fun removingAndClearing() = runTest {
        charts("a", "b")
        dao.append(items("a", "b"))

        dao.delete("a")
        assertEquals(listOf("b"), dao.observe().first().map { it.chartId })

        dao.clear()
        assertNull(dao.first())
    }
}
