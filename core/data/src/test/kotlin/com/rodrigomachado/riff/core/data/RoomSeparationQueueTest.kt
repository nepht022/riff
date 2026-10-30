package com.rodrigomachado.riff.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rodrigomachado.riff.core.database.ChartEntity
import com.rodrigomachado.riff.core.database.RiffDatabase
import com.rodrigomachado.riff.core.model.QueuedSeparation
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
class RoomSeparationQueueTest {
    private lateinit var db: RiffDatabase
    private lateinit var queue: RoomSeparationQueue

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RiffDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        queue = RoomSeparationQueue(db.separationQueueDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun charts(vararg ids: String) = ids.forEach {
        db.chartDao().replace(ChartEntity(it, "M", "B", null, null, null, "content://$it", "CHART", 1), emptyList())
    }

    @Test
    fun theSongsComeOutInTheOrderTheyWentIn() = runTest {
        charts("a", "b")
        queue.add(listOf(QueuedSeparation("b", "Bê"), QueuedSeparation("a", "A")))

        assertEquals(listOf(QueuedSeparation("b", "Bê"), QueuedSeparation("a", "A")), queue.items.first())
        assertEquals(QueuedSeparation("b", "Bê"), queue.next())

        queue.remove("b")
        assertEquals(QueuedSeparation("a", "A"), queue.next())

        queue.clear()
        assertNull(queue.next())
    }
}
