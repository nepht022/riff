package com.rodrigomachado.riff.core.data.queue

import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.TrackMatchRepository
import com.rodrigomachado.riff.core.data.download.ChartDownloadException
import com.rodrigomachado.riff.core.data.download.ChartDownloader
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.spotify.QueueRead
import com.rodrigomachado.riff.core.spotify.SpotifyQueue
import com.rodrigomachado.riff.core.spotify.SpotifyRemote
import kotlinx.coroutines.flow.MutableStateFlow

fun playing(uri: String) = PlaybackState(uri, "Música $uri", "Banda", 200_000, 0, false, 0)

class FakeRemote : SpotifyRemote {
    override val connection = MutableStateFlow<SpotifyConnection>(SpotifyConnection.Connected)
    override val playback = MutableStateFlow<PlaybackState?>(null)
    var connects = 0

    override fun connect() {
        connects++
    }

    override suspend fun refresh(): PlaybackState? = playback.value

    override fun play(trackUri: String) = Unit

    override fun pause() = Unit

    override fun resume() = Unit

    override fun seekTo(positionMs: Long) = Unit

    override fun skipNext() = Unit

    override fun skipPrevious() = Unit
}

class FakeQueue : SpotifyQueue {
    var next: QueueRead = QueueRead.Tracks(emptyList())
    override val changes = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    var reads = 0
    var failOnce: RuntimeException? = null

    override suspend fun next(count: Int): QueueRead {
        reads++
        failOnce?.let {
            failOnce = null
            throw it
        }
        return next
    }
}

class FakeSettings(initial: Settings) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun update(transform: (Settings) -> Settings) {
        settings.value = transform(settings.value)
    }
}

class FakeMatches : TrackMatchRepository {
    val statuses = mutableMapOf<String, MatchStatus>()

    /** O casamento quando a duração não é conhecida (a fila da sessão não traz): sem ela, outra versão passa. */
    val statusesWithoutDuration = mutableMapOf<String, MatchStatus>()
    override val changes = MutableStateFlow(0)

    override suspend fun matchFor(track: PlaybackState): MatchStatus =
        (if (track.durationMs == 0L) statusesWithoutDuration[track.trackUri] else null)
            ?: statuses[track.trackUri] ?: MatchStatus.NoMatch

    val suggestions = mutableListOf<Pair<String, String>>()

    override suspend fun save(spotifyTrackUri: String, chartId: String, confidence: Float, isManual: Boolean) = Unit

    override suspend fun saveSuggestion(spotifyTrackUri: String, chartId: String, confidence: Float) {
        suggestions += spotifyTrackUri to chartId
    }

    override suspend fun offsetFor(spotifyTrackUri: String, chartId: String): Int? = null

    override suspend fun saveOffset(spotifyTrackUri: String, chartId: String, offsetMs: Int) = Unit
}

class FakeDownloader : ChartDownloader {
    val downloaded = mutableListOf<Pair<String, String?>>()
    var failure: ChartDownloadException? = null

    /** Segura o download no meio (para a partida começar durante ele). */
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var cancelled = 0

    override suspend fun download(chart: EncoreChart, matchTrackUri: String?, onProgress: (Float) -> Unit): Chart {
        failure?.let { throw it }
        try {
            gate?.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            cancelled++
            throw e
        }
        downloaded += chart.md5 to matchTrackUri
        val id = "chart-${chart.md5}"
        return Chart(id, chart.name, chart.artist, null, null, null, "f", ChartFormat.CHART, chart.guitarDifficulties)
    }
}

class MemoryQueueDownloads : QueueDownloads {
    val rows = mutableMapOf<String, QueueDownload>()

    override suspend fun add(chartId: String, trackUri: String, at: Long) {
        rows[chartId] = QueueDownload(chartId, trackUri, at, played = false)
    }

    override suspend fun markPlayed(chartId: String) {
        rows[chartId]?.let { rows[chartId] = it.copy(played = true) }
    }

    override suspend fun unplayed(): List<QueueDownload> = rows.values.filter {
        !it.played
    }.sortedBy { it.downloadedAt }

    override suspend fun remove(chartIds: Collection<String>) {
        chartIds.forEach(rows::remove)
    }

    override suspend fun forTrack(trackUri: String): QueueDownload? = rows.values.firstOrNull {
        it.trackUri == trackUri
    }
}

class FakeConditions : DownloadConditions {
    var unmetered = true
    var enoughSpace = true
    var writableFolder = true

    override suspend fun canDownload(useMobileData: Boolean) =
        (unmetered || useMobileData) && enoughSpace && writableFolder
}
