package com.rodrigomachado.riff.feature.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.data.NowPlayingRepository
import com.rodrigomachado.riff.core.data.TrackMatchRepository
import com.rodrigomachado.riff.core.model.NowPlaying
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.model.SpotifyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NowPlayingUiState {
    data object Connecting : NowPlayingUiState

    data class Unavailable(val error: SpotifyError) : NowPlayingUiState

    data object NothingPlaying : NowPlayingUiState

    data class Playing(val nowPlaying: NowPlaying) : NowPlayingUiState
}

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val repository: NowPlayingRepository,
    private val matches: TrackMatchRepository,
) : ViewModel() {
    val uiState: StateFlow<NowPlayingUiState> =
        combine(repository.connection, repository.nowPlaying) { connection, nowPlaying ->
            when (connection) {
                SpotifyConnection.Idle, SpotifyConnection.Connecting -> NowPlayingUiState.Connecting

                is SpotifyConnection.Failed -> NowPlayingUiState.Unavailable(connection.error)

                SpotifyConnection.Connected ->
                    nowPlaying?.let { NowPlayingUiState.Playing(it) } ?: NowPlayingUiState.NothingPlaying
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NowPlayingUiState.Connecting)

    init {
        repository.connect()
    }

    fun previous() = repository.previous()

    fun next() = repository.next()

    /** Pausado, volta a tocar; tocando, pausa. */
    fun playPause(isPaused: Boolean) = if (isPaused) repository.resume() else repository.pause()

    fun retry() = repository.connect()

    /**
     * O jogador voltou ao Riff: se o Spotify faltava ou estava deslogado, ele pode ter resolvido lá fora; tenta de novo
     * sem pedir o toque em "Tentar de novo". O que não muda com o app aberto (Client ID ausente) fica como está.
     */
    fun onReturn() {
        val error = (uiState.value as? NowPlayingUiState.Unavailable)?.error ?: return
        if (error == SpotifyError.NOT_INSTALLED || error == SpotifyError.NOT_LOGGED_IN) repository.connect()
    }

    /** O jogador confirmou o mapa sugerido (confiança média, spec §5). */
    fun confirmSuggestion(trackUri: String, chartId: String, confidence: Float) {
        viewModelScope.launch { matches.save(trackUri, chartId, confidence, isManual = false) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
