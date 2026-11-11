package com.rodrigomachado.riff.core.audio

/** Música da partida: o áudio da pasta do mapa (um arquivo só ou as faixas separadas). */
interface SongPlayer {
    /** @throws java.io.IOException se o arquivo não puder ser aberto ou decodificado. */
    suspend fun prepare(uri: String)

    fun play()

    fun pause()

    /** Leva a música para [positionMs]; usado com a música pausada. Quem não sabe reposicionar ignora. */
    fun seekTo(positionMs: Long) = Unit

    /** Volume geral do app, de 0 a 1. */
    fun setVolume(volume: Float) = Unit

    fun release()

    val positionMs: Long

    val isPlaying: Boolean

    /** Mandou tocar, mas o som ainda não saiu: a posição fica parada e o jogo espera em vez de adiantar. */
    val isStarting: Boolean get() = false

    val isCompleted: Boolean

    /**
     * Aquece a saída antes do primeiro [play] (partida de sala): a saída começa a tocar silêncio, já pronta, para o
     * [play] no instante combinado não esperar o arranque do som (no PC, centenas de ms na primeira vez). A posição
     * fica em 0 e [isPlaying] falso até o [play]. Quem não sabe aquecer ignora.
     */
    fun warmUp() = Unit

    /**
     * Aquecida ([warmUp]) e já tocando o silêncio: quanto o som do [play] leva até o alto-falante, em ms (o silêncio
     * ainda na fila da saída). null = não se sabe (sem aquecer, ainda arrancando, ou o player não informa).
     */
    val outputLatencyMs: Long? get() = null
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
