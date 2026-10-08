package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.model.NowPlaying
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.spotify.SpotifyRemote
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton

interface NowPlayingRepository {
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

    // Recalcula quando a faixa muda, quando a biblioteca muda (importação) e quando um casamento é salvo.
    @OptIn(ExperimentalCoroutinesApi::class)
    override val nowPlaying: Flow<NowPlaying?> =
        combine(remote.playback, charts.observeCharts(), matches.changes) { playback, _, _ -> playback }
            .mapLatest { playback -> playback?.let { NowPlaying(it, matches.matchFor(it)) } }

    override fun connect() = remote.connect()
}
