package com.rodrigomachado.riff.core.data.queue

import com.rodrigomachado.riff.core.data.library.ChartDeleter
import com.rodrigomachado.riff.core.data.library.DeletionResult
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueueCleanerTest {
    private val rows = MemoryQueueDownloads()
    private val deleter = FakeChartDeleter(rows)
    private val settings = FakeSettings(Settings.DEFAULT.copy(queuePrefetch = true))
    private val matchActivity = DefaultMatchActivity(rows)
    private val cleaner = QueueCleaner(rows, deleter, settings, matchActivity)

    private suspend fun downloadedFor(vararg tracks: String) =
        tracks.forEachIndexed { i, track -> rows.add("chart-$track", track, at = i.toLong()) }

    @Test
    fun aSongThatPassedWithoutPlayingLosesItsMap() = runTest {
        downloadedFor("a", "b", "c")

        // "a" tocou e passou: agora toca "b", e a fila mostra "c" e "d".
        cleaner.keepOnly(currentTrackUri = "b", window = listOf("c", "d"))

        assertEquals(listOf("chart-a"), deleter.deleted)
    }

    @Test
    fun aPlayedMapIsNeverDeleted() = runTest {
        downloadedFor("a")
        rows.markPlayed("chart-a")

        cleaner.keepOnly(currentTrackUri = "z", window = emptyList())

        assertTrue(deleter.deleted.isEmpty())
    }

    @Test
    fun aMapThatLeftTheWindowWithoutPlayingIsDeleted() = runTest {
        downloadedFor("b", "c")

        // A fila mudou (outra playlist): "c" não toca mais nem está entre as próximas.
        cleaner.keepOnly(currentTrackUri = "b", window = listOf("x", "y"))

        assertEquals(listOf("chart-c"), deleter.deleted)
    }

    @Test
    fun theCurrentSongsAndTheNextOnesMapsAreKept() = runTest {
        downloadedFor("now", "next")

        cleaner.keepOnly(currentTrackUri = "now", window = listOf("next"))

        assertTrue(deleter.deleted.isEmpty())
    }

    @Test
    fun withDeletingOffNothingIsDeleted() = runTest {
        downloadedFor("a")
        settings.settings.value = settings.settings.value.copy(queueDeleteUnplayed = false)

        cleaner.keepOnly(currentTrackUri = "z", window = emptyList())
        cleaner.atStartup()

        assertTrue(deleter.deleted.isEmpty())
    }

    /** Baixados na mão (ou com "apagar" desligada) não têm registro da fila: nunca vão para o apagador. */
    @Test
    fun manualDownloadsAreNeverDeleted() = runTest {
        cleaner.keepOnly(currentTrackUri = "z", window = emptyList())

        assertEquals(0, deleter.calls) // nada da fila para apagar: o apagador nem é chamado
    }

    @Test
    fun withPrefetchOnStartupWaitsForTheFirstQueueRead() = runTest {
        downloadedFor("a")

        cleaner.atStartup()

        assertTrue(deleter.deleted.isEmpty())
    }

    @Test
    fun turningPrefetchOffDeletesLeftoversAtTheNextStart() = runTest {
        downloadedFor("a", "b")
        rows.markPlayed("chart-b")
        settings.settings.value = settings.settings.value.copy(queuePrefetch = false)

        cleaner.atStartup()

        assertEquals(listOf("chart-a"), deleter.deleted)
    }

    /** Tocou em Jogar e o Spotify trocou de música durante o carregamento: o mapa aberto não some. */
    @Test
    fun aMapBeingOpenedIsNotDeleted() = runTest {
        downloadedFor("a")
        matchActivity.enter("chart-a")

        cleaner.keepOnly(currentTrackUri = "b", window = emptyList())

        assertTrue(deleter.deleted.isEmpty())
    }

    @Test
    fun nothingIsDeletedDuringAMatch() = runTest {
        downloadedFor("a")
        matchActivity.enter("outro")

        cleaner.keepOnly(currentTrackUri = "b", window = emptyList())

        assertTrue(deleter.deleted.isEmpty())
    }
}

class FakeChartDeleter(private val rows: MemoryQueueDownloads) : ChartDeleter {
    val deleted = mutableListOf<String>()
    var calls = 0

    /** Não consegue apagar: os registros ficam. */
    var failing = false

    override suspend fun alsoDeleted(chartIds: Collection<String>): Int = 0

    override suspend fun delete(chartIds: Collection<String>): DeletionResult {
        calls++
        if (failing) return DeletionResult(0, chartIds.size)
        deleted += chartIds
        rows.remove(chartIds) // no banco de verdade a linha sai junto com o mapa
        return DeletionResult(chartIds.size, 0)
    }
}
