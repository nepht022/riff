package com.rodrigomachado.riff.core.audio

/** Música da partida. Hoje é o áudio da pasta do mapa; o Spotify implementa a mesma ideia no plano seguinte. */
interface SongPlayer {
    /** @throws java.io.IOException se o arquivo não puder ser aberto ou decodificado. */
    suspend fun prepare(uri: String)

    fun play()

    fun pause()

    /** Volume geral do app, de 0 a 1. Quem não controla o volume (o Spotify, pelo SDK) ignora. */
    fun setVolume(volume: Float) = Unit

    fun release()

    val positionMs: Long

    val isPlaying: Boolean

    val isCompleted: Boolean
}

/** Sons de acerto e erro, tocados no instante do toque. */
interface SfxPlayer {
    /** Acerto na [lane] (0 a 4): cada trilha tem a sua nota. */
    fun hit(lane: Int)

    fun miss()

    /** Volumes de acerto e de erro, de 0 (desligado) a 1, já multiplicados pelo volume geral. */
    fun setVolumes(hit: Float, miss: Float)

    fun release()
}

fun interface SongPlayerFactory {
    fun create(): SongPlayer
}

fun interface SfxPlayerFactory {
    fun create(): SfxPlayer
}
