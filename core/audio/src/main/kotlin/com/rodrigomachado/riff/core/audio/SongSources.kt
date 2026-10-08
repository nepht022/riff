package com.rodrigomachado.riff.core.audio

import javax.inject.Qualifier

/** Música da pasta do mapa (MediaPlayer). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalSong

/** Música tocando no Spotify. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SpotifySong
