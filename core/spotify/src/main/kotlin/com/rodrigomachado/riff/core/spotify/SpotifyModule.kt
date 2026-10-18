package com.rodrigomachado.riff.core.spotify

import android.os.SystemClock
import com.rodrigomachado.riff.core.audio.SongPlayerFactory
import com.rodrigomachado.riff.core.audio.SpotifySong
import com.rodrigomachado.riff.core.spotify.session.AndroidSpotifySession
import com.rodrigomachado.riff.core.spotify.session.MediaSessionSpotify
import com.rodrigomachado.riff.core.spotify.session.SESSION_CORRECTION_MS
import com.rodrigomachado.riff.core.spotify.session.SpotifySession
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SpotifyBindsModule {
    @Binds
    abstract fun spotifySession(impl: AndroidSpotifySession): SpotifySession
}

@Module
@InstallIn(SingletonComponent::class)
object SpotifyProvidesModule {
    @Provides
    fun config(): SpotifyConfig = SpotifyConfig(BuildConfig.SPOTIFY_CLIENT_ID, BuildConfig.SPOTIFY_REDIRECT_URI)

    /** O Spotify pela sessão de mídia do Android (sem SDK nem cadastro no Dashboard). */
    @Provides
    @Singleton
    fun spotifyRemote(session: SpotifySession): SpotifyRemote = MediaSessionSpotify(
        session,
        SESSION_CORRECTION_MS,
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )

    @Provides
    @SpotifySong
    fun spotifySongPlayers(remote: SpotifyRemote): SongPlayerFactory = SongPlayerFactory {
        SpotifySongPlayer(
            remote,
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            SystemClock::elapsedRealtime,
        )
    }
}
