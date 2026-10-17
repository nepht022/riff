package com.rodrigomachado.riff.core.data.queue

import com.rodrigomachado.riff.core.data.download.ChartDownloadException
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.spotify.web.QueueRead
import com.rodrigomachado.riff.core.spotify.web.QueuedTrack
import com.rodrigomachado.riff.core.spotify.web.WebAuthState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QueuePrefetcherTest {
    private val remote = FakeRemote()
    private val queue = FakeQueue()
    private val auth = FakeAuth()
    private val settings = FakeSettings(Settings.DEFAULT.copy(queuePrefetch = true))
    private val matches = FakeMatches()
    private val encore = FakeEncoreSearch()
    private val downloader = FakeDownloader()
    private val rows = MemoryQueueDownloads()
    private val conditions = FakeConditions()
    private val matchActivity = DefaultMatchActivity(rows)
    private val deleter = FakeChartDeleter(rows)

    private fun queued(vararg uris: String) = QueueRead.Tracks(
        uris.map { QueuedTrack(it, "Música $it", "Banda", 200_000) },
    )

    private fun encoreHas(vararg uris: String) {
        encore.results = emptyList()
        encore.byQuery = uris.associate { "Banda Música $it" to listOf(chartFor(it)) }
    }

    private fun chartFor(uri: String) = EncoreChart(
        "md5-$uri",
        "Música $uri",
        "Banda",
        null,
        200_000,
        setOf(Difficulty.EXPERT),
        hasVideoBackground = false,
    )

    private fun TestScope.start() {
        QueuePrefetcher(
            remote, queue, auth, settings, matches, QueueChartPicker(encore), downloader, rows, conditions,
            matchActivity, QueueCleaner(rows, deleter, settings, matchActivity), clock = { testScheduler.currentTime },
        ).start(backgroundScope)
        runCurrent()
    }

    private fun TestScope.nowPlaying(uri: String) {
        remote.playback.value = playing(uri)
        runCurrent()
    }

    @Test
    fun withTheOptionOffNothingIsRead() = runTest {
        settings.settings.value = Settings.DEFAULT
        start()
        nowPlaying("now")

        assertEquals(0, queue.reads)
    }

    @Test
    fun onSongChangeTheNextTwoGetTheirMapsInOrder() = runTest {
        queue.next = queued("a", "b")
        encoreHas("a", "b")
        start()

        nowPlaying("now")

        assertEquals(listOf("md5-a" to "a", "md5-b" to "b"), downloader.downloaded)
    }

    @Test
    fun songsWithAMapAreSkipped() = runTest {
        queue.next = queued("a", "b")
        encoreHas("a", "b")
        matches.statuses["a"] = MatchStatus.Matched(libraryChart("x"), 0)
        start()

        nowPlaying("now")

        assertEquals(listOf("md5-b" to "b"), downloader.downloaded)
    }

    @Test
    fun aSuggestedMapFollowsTheOption() = runTest {
        queue.next = queued("a")
        encoreHas("a")
        matches.statuses["a"] = MatchStatus.Suggested(libraryChart("x"), 0.8f)
        settings.settings.value = settings.settings.value.copy(queueReplaceSuggested = false)
        start()
        nowPlaying("now")
        assertTrue(downloader.downloaded.isEmpty())

        settings.settings.value = settings.settings.value.copy(queueReplaceSuggested = true)
        nowPlaying("now2")
        assertEquals(listOf("md5-a" to "a"), downloader.downloaded)
    }

    @Test
    fun aTriedSongIsNotSearchedAgain() = runTest {
        queue.next = queued("a")
        start()
        nowPlaying("now")
        nowPlaying("now2")

        assertEquals(1, encore.queries.size)
    }

    @Test
    fun aFailedDownloadCountsAsTried() = runTest {
        queue.next = queued("a")
        encoreHas("a")
        downloader.failure = ChartDownloadException(ChartDownloadException.Reason.NETWORK, null)
        start()
        nowPlaying("now")
        downloader.failure = null
        nowPlaying("now2")

        assertEquals(1, encore.queries.size)
        assertTrue(downloader.downloaded.isEmpty())
    }

    @Test
    fun theQueueIsReadAgainAfter60Seconds() = runTest {
        start()
        nowPlaying("now")
        assertEquals(1, queue.reads)

        advanceTimeBy(60_001)

        assertEquals(2, queue.reads)
    }

    @Test
    fun nothingStartsDuringAMatchAndPendingOnesWaitForTheEnd() = runTest {
        queue.next = queued("a")
        encoreHas("a")
        matchActivity.enter("partida")
        start()
        nowPlaying("now")
        assertTrue(downloader.downloaded.isEmpty())
        assertTrue(encore.queries.isEmpty())

        matchActivity.leave("partida")
        runCurrent()

        assertEquals(listOf("md5-a" to "a"), downloader.downloaded)
    }

    @Test
    fun withoutMobileDataNothingIsSearchedOrDownloadedOnMeteredNetwork() = runTest {
        queue.next = queued("a")
        encoreHas("a")
        conditions.unmetered = false
        start()
        nowPlaying("now")

        assertTrue(encore.queries.isEmpty())
        assertTrue(downloader.downloaded.isEmpty())

        settings.settings.value = settings.settings.value.copy(queueUseMobileData = true)
        nowPlaying("now2")
        assertEquals(listOf("md5-a" to "a"), downloader.downloaded)
    }

    /** Se apagar falhar (pasta sem permissão, por exemplo), os mapas da fila não se acumulam: no máximo 3. */
    @Test
    fun atMostThreeDeletableQueueMapsExist() = runTest {
        repeat(3) { rows.add("old$it", "spotify:old$it", at = it.toLong()) }
        deleter.failing = true
        queue.next = queued("a")
        encoreHas("a")
        start()
        nowPlaying("now")

        assertTrue(downloader.downloaded.isEmpty())
    }

    @Test
    fun theCapDoesNotApplyWithDeletingOff() = runTest {
        repeat(3) { rows.add("old$it", "spotify:old$it", at = it.toLong()) }
        settings.settings.value = settings.settings.value.copy(queueDeleteUnplayed = false)
        queue.next = queued("a")
        encoreHas("a")
        start()
        nowPlaying("now")

        assertEquals(listOf("md5-a" to "a"), downloader.downloaded)
    }

    @Test
    fun aRowIsWrittenOnlyWhenDeletingIsOn() = runTest {
        queue.next = queued("a", "b")
        encoreHas("a", "b")
        start()
        nowPlaying("now")
        assertEquals(setOf("chart-md5-a", "chart-md5-b"), rows.rows.keys)

        rows.rows.clear()
        settings.settings.value = settings.settings.value.copy(queueDeleteUnplayed = false)
        queue.next = queued("c")
        encoreHas("c")
        nowPlaying("now2")
        assertTrue(rows.rows.isEmpty())
    }

    @Test
    fun retryAfterIsRespected() = runTest {
        queue.next = QueueRead.RetryAfter(120)
        start()
        nowPlaying("now")
        advanceTimeBy(61_000)
        assertEquals(1, queue.reads)

        advanceTimeBy(60_000)
        assertEquals(2, queue.reads)
    }

    @Test
    fun aLostAuthorizationTurnsTheOptionOff() = runTest {
        queue.next = QueueRead.Unavailable
        auth.state.value = WebAuthState.LOST
        start()
        nowPlaying("now")

        assertFalse(settings.settings.value.queuePrefetch)
    }

    @Test
    fun theFeatureNeverStartsTheSpotifyConnection() = runTest {
        remote.connection.value = com.rodrigomachado.riff.core.model.SpotifyConnection.Idle
        start()
        nowPlaying("now")

        assertEquals(0, remote.connects)
        assertEquals(0, queue.reads)
    }

    private fun libraryChart(id: String) =
        Chart(id, "M", "B", null, null, null, "f", ChartFormat.CHART, setOf(Difficulty.EXPERT))

    @Test
    fun aSongThatPassedWithoutPlayingLosesItsMapAtTheNextRead() = runTest {
        queue.next = queued("a")
        encoreHas("a")
        start()
        nowPlaying("now")
        assertEquals(setOf("chart-md5-a"), rows.rows.keys)

        // "a" tocou e passou sem partida: agora toca "b" e a fila não tem mais "a".
        queue.next = queued("c")
        nowPlaying("a")
        nowPlaying("b")

        assertEquals(listOf("chart-md5-a"), deleter.deleted)
    }

    @Test
    fun leftoversGoAtStartupWhenTheQueueIsOff() = runTest {
        rows.add("sobra", "spotify:velha", at = 1)
        settings.settings.value = Settings.DEFAULT

        start()

        assertEquals(listOf("sobra"), deleter.deleted)
    }

    /** ⏭ na pausa: a partida seguinte começa com um download no meio; ele para e volta depois da partida. */
    @Test
    fun aDownloadInProgressStopsWhenAMatchStartsAndResumesAfter() = runTest {
        queue.next = queued("a")
        encoreHas("a")
        downloader.gate = kotlinx.coroutines.CompletableDeferred()
        start()
        nowPlaying("now")

        matchActivity.enter("partida")
        runCurrent()
        assertEquals(1, downloader.cancelled)

        downloader.gate = null
        matchActivity.leave("partida")
        runCurrent()
        assertEquals(listOf("md5-a" to "a"), downloader.downloaded)
    }

    /** Depois de esperar a partida, tudo é conferido de novo (a rede pode ter virado dados móveis). */
    @Test
    fun afterAMatchTheConditionsAreCheckedAgain() = runTest {
        queue.next = queued("a")
        encoreHas("a")
        downloader.gate = kotlinx.coroutines.CompletableDeferred()
        start()
        nowPlaying("now")
        matchActivity.enter("partida")
        runCurrent()

        conditions.unmetered = false
        downloader.gate = null
        matchActivity.leave("partida")
        runCurrent()

        assertTrue(downloader.downloaded.isEmpty())
    }

    @Test
    fun anErrorInOneReadDoesNotStopTheQueue() = runTest {
        queue.failOnce = IllegalStateException("Keystore indisponível")
        queue.next = queued("a")
        encoreHas("a")
        start()
        nowPlaying("now")
        advanceTimeBy(60_001)

        assertEquals(listOf("md5-a" to "a"), downloader.downloaded)
    }

    /** Opção ligada mas sem autorização (token ilegível depois de um backup restaurado): desliga e não fica muda. */
    @Test
    fun withoutAuthorizationTheOptionIsTurnedOff() = runTest {
        queue.next = QueueRead.Unavailable
        auth.state.value = WebAuthState.NOT_AUTHORIZED
        start()
        nowPlaying("now")

        assertFalse(settings.settings.value.queuePrefetch)
    }
}
