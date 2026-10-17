package com.rodrigomachado.riff.core.data.queue

import android.util.Log
import com.rodrigomachado.riff.core.chart.match.TrackQuery
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.TrackMatchRepository
import com.rodrigomachado.riff.core.data.download.ChartDownloadException
import com.rodrigomachado.riff.core.data.download.ChartDownloader
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.spotify.SpotifyRemote
import com.rodrigomachado.riff.core.spotify.web.QueueRead
import com.rodrigomachado.riff.core.spotify.web.QueuedTrack
import com.rodrigomachado.riff.core.spotify.web.SpotifyQueue
import com.rodrigomachado.riff.core.spotify.web.SpotifyWebAuth
import com.rodrigomachado.riff.core.spotify.web.WebAuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapas da fila do Spotify (spec §5–§6): a cada troca de música e a cada minuto lê as duas próximas da fila e
 * garante o mapa delas, um download por vez, nunca durante uma partida, só com rede e espaço. O que já foi
 * tentado não é pesquisado de novo enquanto o Riff estiver aberto.
 */
@Singleton
class QueuePrefetcher(
    private val remote: SpotifyRemote,
    private val queue: SpotifyQueue,
    private val auth: SpotifyWebAuth,
    private val settings: SettingsRepository,
    private val matches: TrackMatchRepository,
    private val picker: QueueChartPicker,
    private val downloader: ChartDownloader,
    private val downloads: QueueDownloads,
    private val conditions: DownloadConditions,
    private val matchActivity: MatchActivity,
    private val cleaner: QueueCleaner,
    private val clock: () -> Long,
) {
    @Inject constructor(
        remote: SpotifyRemote,
        queue: SpotifyQueue,
        auth: SpotifyWebAuth,
        settings: SettingsRepository,
        matches: TrackMatchRepository,
        picker: QueueChartPicker,
        downloader: ChartDownloader,
        downloads: QueueDownloads,
        conditions: DownloadConditions,
        matchActivity: MatchActivity,
        cleaner: QueueCleaner,
    ) : this(
        remote, queue, auth, settings, matches, picker, downloader, downloads, conditions, matchActivity, cleaner,
        System::currentTimeMillis,
    )

    private val handled = mutableSetOf<String>()
    private val waiting = mutableSetOf<String>()
    private val pending = Channel<QueuedTrack>(Channel.UNLIMITED)
    private var started = false

    /** Uma vez, no começo do app; espera a opção e a conexão com o Spotify (nunca conecta sozinho). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        scope.launch { cleaner.atStartup() }
        // Os downloads seguem mesmo se a música trocar no meio: a fila serial não é cancelada com a leitura.
        scope.launch { for (track in pending) prepare(track) }
        scope.launch {
            combine(
                settings.settings.map { it.queuePrefetch }.distinctUntilChanged(),
                remote.connection.map { it == SpotifyConnection.Connected }.distinctUntilChanged(),
            ) { on, connected -> on && connected }
                .distinctUntilChanged()
                .collectLatest { active -> if (active) follow() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun follow() {
        remote.playback.map { it?.trackUri }.filterNotNull().distinctUntilChanged().collectLatest { current ->
            while (true) delay(read(current))
        }
    }

    /** Lê a fila e devolve quanto esperar até a próxima leitura. */
    private suspend fun read(current: String): Long = when (val read = queue.next(WINDOW)) {
        is QueueRead.Tracks -> {
            // Só com a fila lida dá para saber o que passou ou saiu dela.
            cleaner.keepOnly(current, read.tracks.map { it.uri })
            read.tracks.filter { it.uri !in handled && waiting.add(it.uri) }.forEach { pending.send(it) }
            READ_INTERVAL_MS
        }

        is QueueRead.RetryAfter -> read.seconds * MS_PER_SECOND

        QueueRead.Unavailable -> {
            if (auth.state.value == WebAuthState.LOST) settings.update { it.copy(queuePrefetch = false) }
            READ_INTERVAL_MS
        }
    }

    private suspend fun prepare(track: QueuedTrack) {
        waiting -= track.uri
        val options = settings.settings.first()
        if (!worthTrying(track, options)) return
        matchActivity.inMatch.first { !it }
        if (!conditions.canDownload(options.queueUseMobileData)) return
        handled += track.uri
        val chart = picker.pick(TrackQuery(track.title, track.artist, track.durationMs)) ?: return
        matchActivity.inMatch.first { !it }
        download(track, chart)
    }

    /** Ainda sem mapa, com a opção ligada e sem passar do limite de mapas apagáveis. */
    private suspend fun worthTrying(track: QueuedTrack, options: Settings): Boolean = track.uri !in handled &&
        options.queuePrefetch &&
        needsMap(track, options.queueReplaceSuggested) &&
        !(options.queueDeleteUnplayed && downloads.unplayed().size >= MAX_QUEUE_MAPS)

    private suspend fun download(track: QueuedTrack, chart: EncoreChart) {
        val saved = try {
            downloader.download(chart, track.uri) {}
        } catch (e: ChartDownloadException) {
            Log.w(TAG, "mapa da fila não baixado: ${e.reason}")
            return
        }
        Log.i(TAG, "mapa da fila pronto: ${track.title}")
        if (settings.settings.first().queueDeleteUnplayed) downloads.add(saved.id, track.uri, clock())
    }

    private suspend fun needsMap(track: QueuedTrack, replaceSuggested: Boolean): Boolean =
        when (matches.matchFor(PlaybackState(track.uri, track.title, track.artist, track.durationMs, 0, true, 0))) {
            is MatchStatus.Matched -> {
                handled += track.uri
                false
            }

            is MatchStatus.Suggested -> replaceSuggested

            MatchStatus.NoMatch -> true
        }

    private companion object {
        const val TAG = "RiffQueue"
        const val WINDOW = 2
        const val MAX_QUEUE_MAPS = 3
        const val READ_INTERVAL_MS = 60_000L
        const val MS_PER_SECOND = 1_000L
    }
}
