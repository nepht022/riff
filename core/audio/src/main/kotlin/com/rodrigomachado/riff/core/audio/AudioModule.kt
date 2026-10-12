package com.rodrigomachado.riff.core.audio

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
    fun musicStarts(@ApplicationContext context: Context): MusicStartFinder =
        PcmMusicStartFinder { uri -> MediaCodecPcmSource.open(context, uri) }

    @Provides
    fun playbackInterruptions(@ApplicationContext context: Context): PlaybackInterruptionsFactory =
        PlaybackInterruptionsFactory { AndroidPlaybackInterruptions(context) }

    /** Um só para o app: os sons do menu ficam carregados enquanto ele vive. */
    @Provides
    @Singleton
    fun menuSounds(@ApplicationContext context: Context): MenuSoundPlayer = SoundPoolMenuSounds(context)

    @Provides
    fun audioSinks(): AudioSinkFactory = AudioSinkFactory(::AudioTrackSink)
}
