package com.rodrigomachado.riff.core.data.queue

import com.rodrigomachado.riff.core.data.download.ChartDownloadException
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.spotify.QueueRead
import com.rodrigomachado.riff.core.spotify.QueuedTrack
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QueuePrefetcherTest {
    private val remote = FakeRemote()
    private val queue = FakeQueue()
    private val settings = FakeSettings(Settings.DEFAULT.copy(queuePrefetch = true))
    private val matches = FakeMatches()
    private val encore = FakeEncoreSearch()
    private val downloader = FakeDownloader()
    private val rows = MemoryQueueDownloads()
    private val conditions = FakeConditions()
    private val matchActivity = DefaultMatchActivity(rows)
    private val deleter = FakeChartDeleter(rows)
    private val misses = MemoryQueueMisses()
    private val preparing = MemoryQueuePreparing()

    init {
        // As músicas "tocando" dos testes já têm mapa: a fila também prepara a música atual, e os testes olham a fila.
        listOf("now", "now2").forEach { matches.statuses[it] = MatchStatus.Matched(libraryChart(it), 0) }
    }

    private fun queued(vararg uris: String) = QueueRead.Tracks(
        uris.map { QueuedTrack(it, "Música $it", "Banda", 200_000) },
    )

    private fun encoreHas(vararg uris: String) {
        encore.results = emptyList()
        encore.byQuery = uris.associate { "Banda Música $it" to listOf(chartFor(it)) }
    }

    /** No Encore só com outra duração (a faixa tem 200 s). */
    private fun encoreHasOnlyAnotherDuration(uri: String, durationMs: Long = 230_000) {
        encore.byQuery = mapOf("Banda Música $uri" to listOf(chartFor(uri, durationMs)))
    }

    private fun chartFor(uri: String, durationMs: Long = 200_000) = EncoreChart(
        "md5-$uri",
        "Música $uri",
        "Banda",
        null,
        durationMs,
        setOf(Difficulty.EXPERT),
        hasVideoBackground = false,
    )

    private fun TestScope.start() {
        QueuePrefetcher(
            remote, queue, settings, matches, QueueChartPicker(encore), downloader, rows, conditions,
            QueueCleaner(rows, deleter, settings, matchActivity), misses, preparing,
            clock = { testScheduler.currentTime },
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
    fun withoutTheSameDurationTheClosestArrivesAsASuggestion() = runTest {
        queue.next = queued("a")
        encoreHasOnlyAnotherDuration("a")
        start()

        nowPlaying("now")

        // Baixado sem casar com a faixa: o casamento fica como sugestão, para confirmar no Tocando agora.
        assertEquals(listOf("md5-a" to null), downloader.downloaded)
        assertEquals(listOf("a" to "chart-md5-a"), matches.suggestions)
        assertEquals("a", rows.rows["chart-md5-a"]?.trackUri) // e sai sozinho se a música passar sem jogar
    }

    @Test
    fun withTheClosestOptionOffNothingIsDownloaded() = runTest {
        settings.settings.value = settings.settings.value.copy(queueClosestDuration = false)
        queue.next = queued("a")
        encoreHasOnlyAnotherDuration("a")
        start()

        nowPlaying("now")

        assertTrue(downloader.downloaded.isEmpty())
        assertTrue(matches.suggestions.isEmpty())
    }

    /** Trocar por uma sugestão que não é mais confiável não ajuda: fica a que já está na biblioteca. */
    @Test
    fun aSuggestionAtLeastAsGoodIsKept() = runTest {
        queue.next = queued("a")
        encoreHasOnlyAnotherDuration("a")
        matches.statuses["a"] = MatchStatus.Suggested(libraryChart("x"), 1f)
        start()

        nowPlaying("now")

        assertTrue(downloader.downloaded.isEmpty())
    }

    /** Beautiful Mourning: a sugestão da biblioteca era fraca; a do Encore (título e artista certos) entra. */
    @Test
    fun aBetterSuggestionReplacesTheLibraryOne() = runTest {
        queue.next = queued("a")
        encoreHasOnlyAnotherDuration("a")
        matches.statuses["a"] = MatchStatus.Suggested(libraryChart("x"), 0.8f)
        start()

        nowPlaying("now")

        assertEquals(listOf("md5-a" to null), downloader.downloaded)
        assertEquals(listOf("a" to "chart-md5-a"), matches.suggestions)
    }

    @Test
    fun aFailedSearchIsTriedAgainAtTheNextRead() = runTest {
        queue.next = queued("a")
        encoreHas("a")
        encore.failure = java.io.IOException("sem rede")
        start()
        nowPlaying("now")
        assertTrue(downloader.downloaded.isEmpty())

        encore.failure = null
        advanceTimeBy(60_001)
        runCurrent()

        assertEquals(listOf("md5-a" to "a"), downloader.downloaded)
    }

    /** O que a fila não achou fica guardado, para o Tocando agora contar quando a música chegar. */
    @Test
    fun whatWasNotFoundIsRemembered() = runTest {
        settings.settings.value = settings.settings.value.copy(queueClosestDuration = false)
        queue.next = queued("nada", "outra")
        encore.results = emptyList()
        encoreHasOnlyAnotherDuration("outra")
        start()

        nowPlaying("now")

        assertEquals(
            mapOf("nada" to QueueMiss.NOTHING_FOUND, "outra" to QueueMiss.ONLY_OTHER_DURATION),
            misses.misses.value,
        )
    }

    @Test
    fun aFailedSearchIsRememberedUntilItWorks() = runTest {
        queue.next = queued("a")
        encoreHas("a")
        encore.failure = java.io.IOException("sem rede")
        start()
        nowPlaying("now")
        assertEquals(mapOf("a" to QueueMiss.SEARCH_FAILED), misses.misses.value)

        encore.failure = null
        advanceTimeBy(60_001)
        runCurrent()

        assertEquals(emptyMap<String, QueueMiss>(), misses.misses.value)
    }

    @Test
    fun aFailedDownloadIsRemembered() = runTest {
        queue.next = queued("a")
        encoreHas("a")
        downloader.failure = ChartDownloadException(ChartDownloadException.Reason.NETWORK, null)
        start()

        nowPlaying("now")

        assertEquals(mapOf("a" to QueueMiss.DOWNLOAD_FAILED), misses.misses.value)
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

    /** A partida não segura mais a fila: as próximas ficam prontas enquanto se joga (spec Spotify contínuo §2.1). */
    @Test
    fun aDownloadHappensDuringAMatch() = runTest {
        queue.next = queued("a")
        encoreHas("a")
        matchActivity.enter("partida")
        start()

        nowPlaying("now")

        assertEquals(listOf("md5-a" to "a"), downloader.downloaded)
    }

    @Test
    fun aDownloadIsNotStoppedWhenAMatchOpens() = runTest {
        queue.next = queued("a")
        encoreHas("a")
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        downloader.gate = gate
        start()
        nowPlaying("now")

        matchActivity.enter("partida")
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(0, downloader.cancelled)
        assertEquals(listOf("md5-a" to "a"), downloader.downloaded)
    }

    /** Na fila de downloads atrás de outra, a música já conta como em preparo (o Tocando agora espera por ela). */
    @Test
    fun aQueuedSongIsPreparingUntilItsTurnEnds() = runTest {
        queue.next = queued("a", "b")
        encoreHas("a", "b")
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        downloader.gate = gate
        start()
        nowPlaying("now")
        assertEquals(setOf("a", "b"), preparing.tracks.value)

        gate.complete(Unit)
        runCurrent()

        assertEquals(emptySet<String>(), preparing.tracks.value)
    }

    @Test
    fun aSongThatCannotDownloadStopsPreparing() = runTest {
        queue.next = queued("a")
        encoreHas("a")
        conditions.unmetered = false
        start()

        nowPlaying("now")

        assertEquals(emptySet<String>(), preparing.tracks.value)
    }

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

    /** Sem sessão (Spotify fechado): a leitura não serve; a opção fica ligada e a próxima leitura tenta de novo. */
    @Test
    fun anUnavailableQueueKeepsTheOption() = runTest {
        queue.next = QueueRead.Unavailable
        start()
        nowPlaying("now")

        assertTrue(settings.settings.value.queuePrefetch)
    }

    /** A fila da sessão não traz a duração: o mapa baixado não vira casamento (outra versão ficaria presa). */
    @Test
    fun aMapForATrackWithoutDurationIsNotSavedAsAMatch() = runTest {
        queue.next = QueueRead.Tracks(listOf(QueuedTrack("a", "Música a", "Banda", durationMs = null)))
        encoreHas("a")
        start()

        nowPlaying("now")

        assertEquals(listOf("md5-a" to null), downloader.downloaded)
        assertTrue(rows.rows.isNotEmpty()) // a limpeza dos não jogados continua sabendo a faixa
    }

    @Test
    fun aTrackWithDurationStillMatchesAsToday() = runTest {
        queue.next = queued("a")
        encoreHas("a")
        start()

        nowPlaying("now")

        assertEquals(listOf("md5-a" to "a"), downloader.downloaded)
    }

    @Test
    fun theQueueIsReadAgainWhenItChanges() = runTest {
        start()
        nowPlaying("now")
        val before = queue.reads

        queue.changes.tryEmit(Unit)
        runCurrent()

        assertEquals(before + 1, queue.reads)
    }

    /**
     * Sem a duração, um mapa de outra versão na biblioteca parece "mapa encontrado". Quando a música toca e a duração
     * chega, ele não casa mais: a fila confere de novo e baixa o certo.
     */
    @Test
    fun aMatchSeenWithoutDurationIsCheckedAgainWhenTheSongPlays() = runTest {
        queue.next = QueueRead.Tracks(listOf(QueuedTrack("a", "Música a", "Banda", durationMs = null)))
        matches.statusesWithoutDuration["a"] = MatchStatus.Matched(libraryChart("outra-versao"), 0)
        encoreHas("a")
        start()

        nowPlaying("now")
        assertTrue(downloader.downloaded.isEmpty())

        queue.next = QueueRead.Tracks(emptyList())
        nowPlaying("a") // tocando, com a duração: o mapa da biblioteca não casa
        assertEquals(listOf("md5-a" to "a"), downloader.downloaded)
    }

    /** A música aberta direto no Spotify (que a fila nunca viu) também ganha o mapa. */
    @Test
    fun theCurrentSongWithoutAMapIsPreparedToo() = runTest {
        encoreHas("x")
        start()

        nowPlaying("x")

        assertEquals(listOf("md5-x" to "x"), downloader.downloaded)
    }

    @Test
    fun theCurrentSongWithAMapIsLeftAlone() = runTest {
        encoreHas("x")
        matches.statuses["x"] = MatchStatus.Matched(libraryChart("x"), 0)
        start()

        nowPlaying("x")

        assertTrue(downloader.downloaded.isEmpty())
    }

    /** A mesma música esperando na fila sem a duração não pode engolir a conferência dela tocando, com a duração. */
    @Test
    fun aSongWaitingWithoutDurationIsStillCheckedWhenItPlays() = runTest {
        downloader.gate = kotlinx.coroutines.CompletableDeferred()
        queue.next = QueueRead.Tracks(
            listOf(
                QueuedTrack("z", "Música z", "Banda", durationMs = null),
                QueuedTrack("a", "Música a", "Banda", durationMs = null),
            ),
        )
        matches.statusesWithoutDuration["a"] = MatchStatus.Matched(libraryChart("outra-versao"), 0)
        encoreHas("z", "a")
        start()
        nowPlaying("now") // "z" baixando (segurado), "a" esperando a vez sem a duração

        nowPlaying("a") // tocando, com a duração
        downloader.gate!!.complete(Unit)
        runCurrent()

        assertTrue(("md5-a" to "a") in downloader.downloaded)
    }
}
