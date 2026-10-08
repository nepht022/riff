package com.rodrigomachado.riff.core.spotify

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

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
}
