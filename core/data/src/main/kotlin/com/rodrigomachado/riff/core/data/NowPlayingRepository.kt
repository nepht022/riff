package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.NowPlaying
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.spotify.SpotifyRemote
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton

/** Controles do Spotify vistos pelo Riff: o efeito aparece no próprio Spotify e volta pela faixa tocando. */
interface SpotifyControls {
    /** Como o botão do Spotify: passados os primeiros segundos, volta ao começo da atual. */
    fun previous()

    /** Sempre a música anterior (na partida, "voltar" é trocar de música, não recomeçar a mesma). */
    fun previousTrack()

    fun next()

    fun pause()

    fun resume()
}

interface NowPlayingRepository : SpotifyControls {
    val connection: StateFlow<SpotifyConnection>

    /** A faixa tocando no Spotify com o mapa casado; `null` sem faixa. */
    val nowPlaying: Flow<NowPlaying?>

    fun connect()
}

@Singleton
class SpotifyNowPlayingRepository @Inject constructor(
    private val remote: SpotifyRemote,
    private val matches: TrackMatchRepository,
    charts: ChartRepository,
) : NowPlayingRepository {
    override val connection: StateFlow<SpotifyConnection> get() = remote.connection

    // O casamento só é recalculado quando a faixa muda, quando a biblioteca muda (importação) ou quando um casamento
    // é salvo; pausar ou avançar na mesma faixa reaproveita o último (cada busca lê o banco e a lista de mapas).
    @OptIn(ExperimentalCoroutinesApi::class)
    private val matchOfTrack: Flow<Pair<String, MatchStatus>?> =
        combine(remote.playback.distinctUntilChangedBy { it?.trackUri }, charts.observeCharts(), matches.changes) {
                playback,
                _,
                _,
            ->
            playback
        }.mapLatest { playback -> playback?.let { it.trackUri to matches.matchFor(it) } }

    override val nowPlaying: Flow<NowPlaying?> =
        combineTransform(remote.playback, matchOfTrack) { playback, match ->
            when {
                playback == null -> emit(null)

                // Enquanto o casamento da faixa nova não chega, não mostra o da anterior.
                match?.first == playback.trackUri -> emit(NowPlaying(playback, match.second))
            }
        }

    override fun connect() = remote.connect()

    override fun previous() = remote.skipPrevious()

    override fun previousTrack() {
        // No começo da faixa o "anterior" do Spotify vai para a música de antes.
        remote.seekTo(0)
        remote.skipPrevious()
    }

    override fun next() = remote.skipNext()

    override fun pause() = remote.pause()

    override fun resume() = remote.resume()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SpotifyControlsModule {
    @Binds
    abstract fun spotifyControls(impl: SpotifyNowPlayingRepository): SpotifyControls
}
