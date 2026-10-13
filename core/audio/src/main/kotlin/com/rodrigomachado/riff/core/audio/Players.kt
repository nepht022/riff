package com.rodrigomachado.riff.core.audio

/** Música da partida. Hoje é o áudio da pasta do mapa; o Spotify implementa a mesma ideia no plano seguinte. */
interface SongPlayer {
    /** @throws java.io.IOException se o arquivo não puder ser aberto ou decodificado. */
    suspend fun prepare(uri: String)

    fun play()

    fun pause()

    /** Leva a música para [positionMs]; usado com a música pausada. Quem não sabe reposicionar ignora. */
    fun seekTo(positionMs: Long) = Unit

    /** Volume geral do app, de 0 a 1. Quem não controla o volume (o Spotify, pelo SDK) ignora. */
    fun setVolume(volume: Float) = Unit

    fun release()

    val positionMs: Long

    val isPlaying: Boolean

    /** Mandou tocar, mas o som ainda não saiu: a posição fica parada e o jogo espera em vez de adiantar. */
    val isStarting: Boolean get() = false

    val isCompleted: Boolean
}

/** O som de erro, tocado no instante do toque (o acerto não faz som: a guitarra da música é a resposta). */
interface SfxPlayer {
    fun miss()

    /** Volume do erro, de 0 (desligado) a 1, já multiplicado pelo volume geral. */
    fun setMissVolume(volume: Float)

    fun release()
}

fun interface SongPlayerFactory {
    fun create(): SongPlayer
}

fun interface SfxPlayerFactory {
    fun create(): SfxPlayer
}
