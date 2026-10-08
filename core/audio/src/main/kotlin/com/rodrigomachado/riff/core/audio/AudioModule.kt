package com.rodrigomachado.riff.core.audio

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {
    @Provides
    @LocalSong
    fun songPlayers(@ApplicationContext context: Context): SongPlayerFactory = SongPlayerFactory {
        MediaSongPlayer(
            context,
        )
    }

    @Provides
    fun sfxPlayers(@ApplicationContext context: Context): SfxPlayerFactory = SfxPlayerFactory {
        SoundPoolSfxPlayer(
            context,
        )
    }
}
