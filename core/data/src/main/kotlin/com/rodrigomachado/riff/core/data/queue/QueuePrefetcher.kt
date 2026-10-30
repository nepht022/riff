package com.rodrigomachado.riff.core.data.queue

import android.util.Log
import com.rodrigomachado.riff.core.chart.match.TrackQuery
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.TrackMatchRepository
import com.rodrigomachado.riff.core.data.download.ChartDownloadException
import com.rodrigomachado.riff.core.data.download.ChartDownloader
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.spotify.QueueRead
import com.rodrigomachado.riff.core.spotify.QueuedTrack
import com.rodrigomachado.riff.core.spotify.SpotifyQueue
import com.rodrigomachado.riff.core.spotify.SpotifyRemote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapas da fila do Spotify (spec §5–§6): a cada troca de música e a cada minuto lê as duas próximas da fila e
 * garante o mapa delas, um download por vez (também durante a partida), só com rede e espaço. O que já foi
 * tentado não é pesquisado de novo enquanto o Riff estiver aberto (a não ser que a pesquisa tenha falhado).
 * Cada música que fica sem mapa deixa o motivo no registro (RiffQueue).
 */
@Singleton
class QueuePrefetcher(
    private val remote: SpotifyRemote,
    private val queue: SpotifyQueue,
    private val settings: SettingsRepository,
    private val matches: TrackMatchRepository,
    private val picker: QueueChartPicker,
    private val downloader: ChartDownloader,
    private val downloads: QueueDownloads,
    private val conditions: DownloadConditions,
    private val cleaner: QueueCleaner,
    private val misses: QueueMisses,
    private val preparing: QueuePreparing,
    private val visibility: AppVisibility,
    private val clock: () -> Long,
) {
    @Inject constructor(
        remote: SpotifyRemote,
        queue: SpotifyQueue,
        settings: SettingsRepository,
        matches: TrackMatchRepository,
        picker: QueueChartPicker,
        @QueueDownloader downloader: ChartDownloader,
        downloads: QueueDownloads,
        conditions: DownloadConditions,
        cleaner: QueueCleaner,
        misses: QueueMisses,
        preparing: QueuePreparing,
        visibility: AppVisibility,
    ) : this(
        remote, queue, settings, matches, picker, downloader, downloads, conditions, cleaner, misses, preparing,
        visibility, System::currentTimeMillis,
    )

    // Lidos pela leitura da fila e pela fila de downloads, que rodam em paralelo.
    private val handled = HandledTracks()
    private val networkAttempts = ConcurrentHashMap<String, Int>()
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
                try {
                    guarded("download da fila") { prepare(track) }
                } finally {
                    preparing.done(track.uri)
                }
            }
        }
        scope.launch {
            combine(
                settings.settings.map { it.queuePrefetch }.distinctUntilChanged(),
                remote.connection.map { it == SpotifyConnection.Connected }.distinctUntilChanged(),
                visibility.visible().distinctUntilChanged(),
            ) { on, connected, visible -> on && connected && visible }
                .distinctUntilChanged()
                .collectLatest { active -> if (active) follow() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun follow() {
        remote.playback.filterNotNull().distinctUntilChangedBy { it.trackUri }.collectLatest { playing ->
            // A música que está tocando também (aberta direto no Spotify, ou vista na fila sem a duração).
            val current = QueuedTrack(
                playing.trackUri,
                playing.title,
                playing.artist,
                playing.durationMs.takeIf { it > 0 },
            )
            coroutineScope {
                // Ler a fila da sessão é de graça: lê de novo a cada mudança, e de tempos em tempos por segurança.
                launch { queue.changes.collect { guarded("leitura da fila") { read(current) } } }
                while (true) {
                    guarded("leitura da fila") { read(current) }
                    delay(READ_INTERVAL_MS)
                }
            }
        }
    }

    /** Lê a fila; sem sessão agora, tenta na próxima leitura. */
    private suspend fun read(current: QueuedTrack) {
        // A atual a cada leitura: depois de uma falha de rede, é tentada de novo (já decidida, sai na hora).
        enqueue(current)
        val read = queue.next(WINDOW) as? QueueRead.Tracks ?: return
        // Só com a fila lida dá para saber o que passou ou saiu dela.
        cleaner.keepOnly(current.uri, read.tracks.map { it.uri }).forEach(handled::forget)
        read.tracks.forEach { enqueue(it) }
    }

    private suspend fun enqueue(track: QueuedTrack) {
        if (handled.contains(track) || !waiting.add(track.waitingKey)) return
        preparing.start(track.uri)
        pending.send(track)
    }

    /** Também durante uma partida: a próxima música fica pronta enquanto se joga (spec Spotify contínuo §2.1). */
    private suspend fun prepare(track: QueuedTrack) {
        waiting -= track.waitingKey
        if (handled.contains(track)) return
        val options = settings.settings.first()
        val status = statusOf(track)
        if (!wanted(track, status, options) || !canDownload(track, options)) return
        val chosen = picker.choose(TrackQuery(track.title, track.artist, track.durationMs))
        // Só a falha da pesquisa é tentada de novo, na próxima leitura.
        if (chosen != QueuePick.SearchFailed) handled.add(track)
        val target = chosen.target(status, options)
        if (target == null) {
            Log.i(TAG, "${track.label()} sem mapa: ${whyNoMap(chosen, status)}")
            chosen.miss(status)?.let { misses.record(track.uri, it) }
            return
        }
        download(track, target)
    }

    /** Opção ligada, ainda sem mapa e sem passar do limite de mapas apagáveis. */
    private suspend fun wanted(track: QueuedTrack, status: MatchStatus, options: Settings): Boolean {
        if (!options.queuePrefetch || !needsMap(track, status, options.queueReplaceSuggested)) return false
        val full = options.queueDeleteUnplayed && downloads.unplayed().size >= MAX_QUEUE_MAPS
        if (full) Log.i(TAG, "${track.label()} espera: já há $MAX_QUEUE_MAPS mapas da fila não jogados")
        return !full
    }

    private suspend fun canDownload(track: QueuedTrack, options: Settings): Boolean =
        conditions.canDownload(options.queueUseMobileData).also {
            if (!it) Log.i(TAG, "${track.label()} espera: sem Wi-Fi (ou dados móveis), espaço ou pasta gravável")
        }

    private suspend fun download(track: QueuedTrack, target: QueueTarget) {
        val suggestion = target.suggestion
        val saved = try {
            // Com outra duração, o mapa não casa com a faixa: fica como sugestão, para confirmar no Tocando agora.
            // Sem a duração (a fila da sessão não traz), também não: ao tocar, o casamento automático decide.
            val matchTo = track.uri.takeIf { suggestion == null && track.durationMs != null }
            downloader.download(target.chart, matchTo) {}
        } catch (e: ChartDownloadException) {
            Log.w(TAG, "${track.label()}: mapa da fila não baixado: ${e.reason}")
            misses.record(track.uri, QueueMiss.DOWNLOAD_FAILED)
            // Rede caiu ou o Encore falhou agora: tenta de novo na próxima leitura (arquivo quebrado não melhora), até
            // MAX_NETWORK_ATTEMPTS vezes por música (um mapa que sempre falha não vira pesquisa a cada leitura).
            val attempts = networkAttempts.merge(track.uri, 1, Int::plus) ?: 1
            val retry = e.reason == ChartDownloadException.Reason.NETWORK && attempts < MAX_NETWORK_ATTEMPTS
            if (retry) handled.remove(track)
            return
        }
        misses.clear(track.uri)
        suggestion?.let { matches.saveSuggestion(track.uri, saved.id, it) }
        Log.i(
            TAG,
            "${track.label()}: mapa da fila pronto" + if (suggestion != null) " (sugestão, outra duração)" else "",
        )
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

    private suspend fun statusOf(track: QueuedTrack): MatchStatus =
        matches.matchFor(track.uri, TrackQuery(track.title, track.artist, track.durationMs))

    private fun needsMap(track: QueuedTrack, status: MatchStatus, replaceSuggested: Boolean): Boolean = when (status) {
        is MatchStatus.Matched -> {
            handled.add(track)
            false
        }

        is MatchStatus.Suggested -> replaceSuggested

        MatchStatus.NoMatch -> true
    }

    private companion object {
        const val TAG = "RiffQueue"
        const val WINDOW = 2
        const val MAX_QUEUE_MAPS = 3
        const val MAX_NETWORK_ATTEMPTS = 3
        const val READ_INTERVAL_MS = 60_000L
    }
}

/** O mapa escolhido e, se veio com outra duração, a semelhança com que fica sugerido. */
internal class QueueTarget(val chart: EncoreChart, val suggestion: Float?)

/** O que baixar; nada se não há mapa confiável, ou se só há outra duração e as opções dizem para não baixar. */
internal fun QueuePick.target(status: MatchStatus, options: Settings): QueueTarget? = when (this) {
    is QueuePick.Exact -> QueueTarget(chart, suggestion = null)

    is QueuePick.Closest -> QueueTarget(chart, confidence).takeIf {
        // Trocar por uma sugestão que não é mais confiável não ajuda.
        options.queueClosestDuration && !status.suggestsAtLeast(confidence)
    }

    QueuePick.NothingReliable, QueuePick.SearchFailed -> null
}

/** O que o Tocando agora conta; nada quando a música já tem uma sugestão na biblioteca. */
internal fun QueuePick.miss(status: MatchStatus): QueueMiss? = when (this) {
    QueuePick.NothingReliable -> QueueMiss.NOTHING_FOUND
    QueuePick.SearchFailed -> QueueMiss.SEARCH_FAILED
    is QueuePick.Closest -> QueueMiss.ONLY_OTHER_DURATION.takeIf { status !is MatchStatus.Suggested }
    is QueuePick.Exact -> null
}

/** A biblioteca já sugere um mapa com pelo menos esta confiança. */
private fun MatchStatus.suggestsAtLeast(confidence: Float) =
    this is MatchStatus.Suggested && this.confidence >= confidence

internal fun whyNoMap(pick: QueuePick, status: MatchStatus) = when {
    pick == QueuePick.SearchFailed -> "a pesquisa no Encore falhou (tenta de novo na próxima leitura)"
    pick == QueuePick.NothingReliable -> "nenhum mapa do Encore com título, artista e duração parecidos"
    status is MatchStatus.Suggested -> "só com outra duração, e a biblioteca já tem uma sugestão tão boa"
    else -> "só com outra duração (opção: não baixar nenhum)"
}

/** Na fila de preparo, a mesma música sem e com a duração são dois pedidos (a conferência com duração decide). */
private val QueuedTrack.waitingKey: String get() = if (durationMs == null) "$uri?" else uri

/** No registro, a música aparece pelo id do Spotify, sem título nem artista (o logcat não guarda o que se ouve). */
private fun QueuedTrack.label() = "faixa ${uri.substringAfterLast(':').take(LABEL_ID_CHARS)}"

private const val LABEL_ID_CHARS = 6

/**
 * As músicas já decididas (com mapa, baixado ou sem mapa confiável). As decididas sem a duração (a fila da sessão
 * não traz) valem para a fila, mas não quando a música toca e a duração chega: um mapa de outra versão parecia
 * "encontrado".
 */
private class HandledTracks {
    private val withDuration: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val withoutDuration: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun contains(track: QueuedTrack) =
        track.uri in withDuration || (track.durationMs == null && track.uri in withoutDuration)

    fun add(track: QueuedTrack) {
        if (track.durationMs == null) withoutDuration += track.uri else withDuration += track.uri
    }

    fun remove(track: QueuedTrack) = forget(track.uri)

    /** O mapa dela foi apagado (passou sem ser jogada): se a música voltar, baixa de novo. */
    fun forget(uri: String) {
        withDuration -= uri
        withoutDuration -= uri
    }
}
