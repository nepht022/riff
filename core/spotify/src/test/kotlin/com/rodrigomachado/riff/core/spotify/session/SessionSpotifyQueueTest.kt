package com.rodrigomachado.riff.core.spotify.session

import com.rodrigomachado.riff.core.spotify.QueueRead
import com.rodrigomachado.riff.core.spotify.QueuedTrack
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionSpotifyQueueTest {
    private val session = FakeSpotifySession()
    private val queue = SessionSpotifyQueue(session)

    private fun item(id: Long, uri: String = "spotify:track:$id") = SessionQueueItem(id, uri, "Música $id", "Banda")

    @Test
    fun theNextAreTheItemsAfterTheActiveOne() = runTest {
        session.queue.value = listOf(item(16), item(17), item(18), item(19))
        session.activeQueueId.value = 16

        assertEquals(
            QueueRead.Tracks(
                listOf(
                    QueuedTrack("spotify:track:17", "Música 17", "Banda", durationMs = null),
                    QueuedTrack("spotify:track:18", "Música 18", "Banda", durationMs = null),
                ),
            ),
            queue.next(2),
        )
    }

    @Test
    fun withoutAnActiveItemThereIsNoNext() = runTest {
        session.queue.value = listOf(item(16), item(17))
        session.activeQueueId.value = null

        assertEquals(QueueRead.Tracks(emptyList()), queue.next(2))
    }

    @Test
    fun episodesAreSkipped() = runTest {
        session.queue.value = listOf(item(1), item(2, "spotify:episode:x"), item(3))
        session.activeQueueId.value = 1

        assertEquals(listOf("spotify:track:3"), (queue.next(2) as QueueRead.Tracks).tracks.map { it.uri })
    }

    @Test
    fun aChangeInTheQueueIsAnnounced() = runTest {
        var announced = false
        backgroundScope.launch { queue.changes.first().also { announced = true } }
        runCurrent()
        session.queue.value = listOf(item(1))
        runCurrent()
        assertTrue(announced)
    }
}
