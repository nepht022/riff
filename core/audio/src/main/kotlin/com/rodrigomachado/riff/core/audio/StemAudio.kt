package com.rodrigomachado.riff.core.audio

import java.io.Closeable
import java.io.IOException

/** Áudio decodificado de uma faixa, em blocos de amostras float intercaladas. */
interface PcmSource : Closeable {
    val sampleRate: Int
    val channelCount: Int

    /** Preenche até [maxFrames] quadros intercalados; devolve menos só no fim da faixa (0 = acabou). */
    fun read(buffer: FloatArray, maxFrames: Int): Int
}

fun interface PcmSourceOpener {
    @Throws(IOException::class)
    fun open(uri: String): PcmSource
}

/** Saída de áudio estéreo. Os quadros já tocados são o relógio da música. */
interface AudioSink {
    fun start()

    fun pause()

    /** Para de vez e desbloqueia um [write] em andamento. */
    fun stop()

    /** Bloqueia até caber; [buffer] é estéreo intercalado. */
    fun write(buffer: FloatArray, frames: Int)

    val playedFrames: Long

    fun release()
}

fun interface AudioSinkFactory {
    fun create(sampleRate: Int): AudioSink
}

/** Liga e desliga a faixa da guitarra (acerto e erro, como no Guitar Hero). */
interface GuitarTrackControl {
    fun setGuitarAudible(audible: Boolean)
}

interface StemPlayer :
    SongPlayer,
    GuitarTrackControl

fun interface StemSongPlayerFactory {
    fun create(guitarUri: String, backingUris: List<String>): StemPlayer
}
