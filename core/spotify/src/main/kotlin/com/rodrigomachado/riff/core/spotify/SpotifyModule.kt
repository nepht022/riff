package com.rodrigomachado.riff.core.spotify

import android.os.SystemClock
import com.rodrigomachado.riff.core.audio.SongPlayerFactory
import com.rodrigomachado.riff.core.audio.SpotifySong
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
abstract class SpotifyBindsModule {
    @Binds
    abstract fun spotifyRemote(impl: AppRemoteSpotify): SpotifyRemote
}

@Module
@InstallIn(SingletonComponent::class)
object SpotifyProvidesModule {
    @Provides
    fun config(): SpotifyConfig = SpotifyConfig(BuildConfig.SPOTIFY_CLIENT_ID, BuildConfig.SPOTIFY_REDIRECT_URI)

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
