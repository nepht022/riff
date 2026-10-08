package com.rodrigomachado.riff.core.audio

/** Música da partida. Hoje é o áudio da pasta do mapa; o Spotify implementa a mesma ideia no plano seguinte. */
interface SongPlayer {
    /** @throws java.io.IOException se o arquivo não puder ser aberto ou decodificado. */
    suspend fun prepare(uri: String)

    fun play()

    fun pause()

    fun release()

    val positionMs: Long

    val isPlaying: Boolean

    val isCompleted: Boolean
}

/** Sons de acerto e erro, tocados no instante do toque. */
interface SfxPlayer {
    fun hit()

    fun miss()

    /** De 0 (desligado) a 1. */
    fun setVolume(volume: Float)

    fun release()
}

fun interface SongPlayerFactory {
    fun create(): SongPlayer
}

fun interface SfxPlayerFactory {
    fun create(): SfxPlayer
}
