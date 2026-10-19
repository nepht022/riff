package com.rodrigomachado.riff.feature.nowplaying

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.data.NowPlayingRepository
import com.rodrigomachado.riff.core.data.TrackMatchRepository
import com.rodrigomachado.riff.core.data.queue.QueueMiss
import com.rodrigomachado.riff.core.data.queue.QueueMisses
import com.rodrigomachado.riff.core.data.queue.QueuePreparing
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.NowPlaying
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.model.SpotifyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed interface NowPlayingUiState {
    data object Connecting : NowPlayingUiState

    data class Unavailable(val error: SpotifyError) : NowPlayingUiState

    data object NothingPlaying : NowPlayingUiState

    /** Anúncio no Spotify (conta grátis): a partida espera ele acabar. */
    data object Ad : NowPlayingUiState

    /**
     * [queueMiss]: a fila procurou um mapa para esta música e não conseguiu; [preparingMap]: a fila ainda está
     * preparando o mapa dela. Os dois só sem mapa.
     */
    data class Playing(
        val nowPlaying: NowPlaying,
        val queueMiss: QueueMiss? = null,
        val preparingMap: Boolean = false,
    ) : NowPlayingUiState
}

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val repository: NowPlayingRepository,
    private val matches: TrackMatchRepository,
    misses: QueueMisses,
    private val preparing: QueuePreparing,
) : ViewModel() {
    val uiState: StateFlow<NowPlayingUiState> =
        combine(
            repository.connection,
            repository.nowPlaying,
            misses.misses,
            preparing.tracks,
            repository.adPlaying,
        ) { connection, nowPlaying, missed, preparingNow, ad ->
            when (connection) {
                SpotifyConnection.Idle, SpotifyConnection.Connecting -> NowPlayingUiState.Connecting

                is SpotifyConnection.Failed -> NowPlayingUiState.Unavailable(connection.error)

                SpotifyConnection.Connected if ad && nowPlaying == null -> NowPlayingUiState.Ad

                SpotifyConnection.Connected ->
                    nowPlaying?.let {
                        val noMap = it.match == MatchStatus.NoMatch
                        val uri = it.playback.trackUri
                        NowPlayingUiState.Playing(
                            it,
                            missed[uri].takeIf { noMap },
                            preparingMap = noMap && uri in preparingNow,
                        )
                    } ?: NowPlayingUiState.NothingPlaying
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NowPlayingUiState.Connecting)

    private val pendingAutoPlay = MutableStateFlow<NowPlayingAutoPlay.Action?>(null)

    /** O que fazer quando a partida trocou de música na pausa (veja [NowPlayingAutoPlay]). */
    val autoPlay: StateFlow<NowPlayingAutoPlay.Action?> = pendingAutoPlay

    init {
        repository.connect()
        // O Spotify passou para outra música: o que estava pronto para a anterior não vale mais.
        viewModelScope.launch {
            repository.nowPlaying.collect { now ->
                val pending = pendingAutoPlay.value
                // Um anúncio (conta grátis) não é outra música: a partida pronta continua esperando.
                if (now == null && repository.adPlaying.value) return@collect
                if (pending != null && now?.playback?.trackUri != pending.trackUri) pendingAutoPlay.value = null
            }
        }
    }

    private var watching: SavedStateHandle? = null
    private var waiting: Job? = null

    /**
     * Acompanha os pedidos deixados pela partida no estado salvo desta tela na navegação
     * ([NowPlayingAutoPlay.request]). Chamado pela tela; a mesma [saved] não é acompanhada duas vezes.
     */
    fun watchAutoPlay(saved: SavedStateHandle) {
        if (watching === saved) return
        watching = saved
        viewModelScope.launch {
            saved.getStateFlow<String?>(NowPlayingAutoPlay.KEY_DIFFICULTY, null).filterNotNull().collect { name ->
                val from = saved.get<String>(NowPlayingAutoPlay.KEY_FROM)
                val waitMs = saved.get<Long>(NowPlayingAutoPlay.KEY_WAIT) ?: NowPlayingAutoPlay.WAIT_MS
                // Usado uma vez só: a próxima troca de música, feita nesta tela, não começa partida nenhuma.
                saved[NowPlayingAutoPlay.KEY_DIFFICULTY] = null
                saved.remove<String>(NowPlayingAutoPlay.KEY_FROM)
                saved.remove<Long>(NowPlayingAutoPlay.KEY_WAIT)
                waiting?.cancel()
                waiting = launch {
                    val next = nextSongAfter(from, waitMs) ?: return@launch
                    val ready = afterPreparing(next)
                    // O Spotify passou de música durante a espera: decidir pela anterior o puxaria de volta para ela.
                    if (repository.playback.value?.trackUri != ready.playback.trackUri) return@launch
                    pendingAutoPlay.value = NowPlayingAutoPlay.decide(ready, Difficulty.valueOf(name))
                }
            }
        }
    }

    /**
     * A próxima música depois de [from], esperando até [waitMs]. Um anúncio (conta grátis) no meio estende a espera até
     * ele acabar: a próxima partida começa depois dele.
     */
    private suspend fun nextSongAfter(from: String?, waitMs: Long): NowPlaying? {
        val isNext = { now: NowPlaying? -> now != null && now.playback.trackUri != from }
        val first = withTimeoutOrNull(waitMs) {
            combine(
                repository.nowPlaying,
                repository.adPlaying,
            ) { now, ad -> now to ad }.first { (now, ad) -> ad || isNext(now) }
        } ?: return null
        if (isNext(first.first)) return first.first
        return withTimeoutOrNull(AD_WAIT_MS) { repository.nowPlaying.first(isNext) }
    }

    /**
     * A fila ainda prepara o mapa desta música: espera ela terminar (sair da tela cancela) e lê o casamento do banco,
     * que pode estar à frente do que esta tela já recebeu.
     */
    private suspend fun afterPreparing(next: NowPlaying): NowPlaying {
        val uri = next.playback.trackUri
        if (uri !in preparing.tracks.value) return next
        preparing.tracks.first { uri !in it }
        return NowPlaying(next.playback, matches.matchFor(next.playback))
    }

    fun autoPlayHandled() {
        pendingAutoPlay.value = null
    }

    /** Saiu da tela: nada começa sozinho depois (nem o que ainda esperava o Spotify trocar de música). */
    fun cancelAutoPlay() {
        waiting?.cancel()
        waiting = null
        pendingAutoPlay.value = null
    }

    fun previous() = repository.previous()

    fun next() = repository.next()

    /** Pausado, volta a tocar; tocando, pausa. */
    fun playPause(isPaused: Boolean) = if (isPaused) repository.resume() else repository.pause()

    /**
     * O jogador voltou ao Riff: ele pode ter instalado o Spotify ou ligado o "Acesso às notificações" lá fora; confere
     * de novo sem pedir toque nenhum.
     */
    fun onReturn() {
        val error = (uiState.value as? NowPlayingUiState.Unavailable)?.error ?: return
        if (error == SpotifyError.NOT_INSTALLED || error == SpotifyError.NO_PERMISSION) repository.connect()
    }

    /** O jogador confirmou o mapa sugerido (confiança média, spec §5). */
    fun confirmSuggestion(trackUri: String, chartId: String, confidence: Float) {
        viewModelScope.launch { matches.save(trackUri, chartId, confidence, isManual = false) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** Quanto esperar a música depois de um anúncio (os do Spotify grátis têm de 15 s a 1 min, às vezes dois). */
        const val AD_WAIT_MS = 180_000L
    }
}
