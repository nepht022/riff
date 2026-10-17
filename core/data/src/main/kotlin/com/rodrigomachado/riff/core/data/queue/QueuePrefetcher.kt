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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
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

    // Lidos pela leitura da fila e pela fila de downloads, que rodam em paralelo.
    private val handled: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val waiting: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val pending = Channel<QueuedTrack>(Channel.UNLIMITED)
    private var started = false

    /** Uma vez, no começo do app; espera a opção e a conexão com o Spotify (nunca conecta sozinho). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        scope.launch { cleaner.atStartup() }
        // Os downloads seguem mesmo se a música trocar no meio: a fila serial não é cancelada com a leitura.
        scope.launch {
            for (track in pending) {
                guarded("download da fila") { prepare(track) }
            }
        }
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
            while (true) delay(guarded("leitura da fila") { read(current) } ?: READ_INTERVAL_MS)
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
            // Sem autorização (perdida, ou token ilegível): desliga, e as Configurações explicam.
            if (auth.state.value != WebAuthState.AUTHORIZED) settings.update { it.copy(queuePrefetch = false) }
            READ_INTERVAL_MS
        }
    }

    private suspend fun prepare(track: QueuedTrack) {
        waiting -= track.uri
        if (track.uri in handled) return
        var chart: EncoreChart? = null
        while (true) {
            // Partida aberta: espera, e depois confere tudo de novo (a rede ou a opção podem ter mudado).
            matchActivity.inMatch.first { !it }
            val options = settings.settings.first()
            if (!wanted(track, options) || !conditions.canDownload(options.queueUseMobileData)) return
            val picked = chart ?: picker.pick(TrackQuery(track.title, track.artist, track.durationMs))
                .also { handled += track.uri } ?: return
            chart = picked
            if (downloadUnlessAMatchOpens(track, picked)) return
        }
    }

    /** Opção ligada, ainda sem mapa e sem passar do limite de mapas apagáveis. */
    private suspend fun wanted(track: QueuedTrack, options: Settings): Boolean = options.queuePrefetch &&
        needsMap(track, options.queueReplaceSuggested) &&
        !(options.queueDeleteUnplayed && downloads.unplayed().size >= MAX_QUEUE_MAPS)

    /** Falso se uma partida abriu no meio: o download para (a pista vem antes) e é refeito depois. */
    @Suppress("SwallowedException") // o cancelamento do download pela partida é o caminho esperado
    private suspend fun downloadUnlessAMatchOpens(track: QueuedTrack, chart: EncoreChart): Boolean = coroutineScope {
        val download = async { download(track, chart) }
        val watcher = launch {
            matchActivity.inMatch.first { it }
            download.cancel()
        }
        val finished = try {
            download.await()
            true
        } catch (e: CancellationException) {
            ensureActive() // cancelado de fora: propaga
            false
        }
        watcher.cancel()
        finished
    }

    private suspend fun download(track: QueuedTrack, chart: EncoreChart) {
        val saved = try {
            downloader.download(chart, track.uri) {}
        } catch (e: ChartDownloadException) {
            Log.w(TAG, "mapa da fila não baixado: ${e.reason}")
            return
        }
        Log.i(TAG, "mapa da fila pronto")
        if (settings.settings.first().queueDeleteUnplayed) downloads.add(saved.id, track.uri, clock())
    }

    /** Um erro inesperado (Keystore, banco) não pode derrubar o app nem parar a fila. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> guarded(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$what falhou: ${e.javaClass.simpleName}")
        null
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
