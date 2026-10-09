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

    @Provides
    fun stemPlayers(@ApplicationContext context: Context): StemSongPlayerFactory =
        StemSongPlayerFactory { guitarUri, backingUris ->
            StemSongPlayer(
                guitarUri,
                backingUris,
                opener = { uri -> MediaCodecPcmSource.open(context, uri) },
                sinks = ::AudioTrackSink,
            )
        }

    @Provides
    fun audioSinks(): AudioSinkFactory = AudioSinkFactory(::AudioTrackSink)
}
