package com.rodrigomachado.riff.core.separation

/**
 * Soma ponderada dos trechos (o `apply_model` do Demucs), em fluxo. A posição `base + i` vive no índice `i`; o que
 * fica antes do próximo trecho já está pronto e sai para o [sink] (dividido pelo peso e desnormalizado). O espaço é o
 * da música deslocada: [Demucs.SAMPLE_RATE]/2 de silêncio antes dela (`max_shift`, com deslocamento 0).
 */
internal class StemAccumulator(private val song: SongPcm, private val sink: StemSink) {
    private val guitar = Array(2) { FloatArray(Demucs.SEGMENT) }
    private val backing = Array(2) { FloatArray(Demucs.SEGMENT) }
    private val weightSum = FloatArray(Demucs.SEGMENT)
    private val outGuitar = FloatArray(2 * Demucs.SEGMENT)
    private val outBacking = FloatArray(2 * Demucs.SEGMENT)
    private var base = 0

    /** Soma o trecho que começa em `base`, já cortado no centro: [length] amostras de cada onda. */
    fun add(length: Int, chunkGuitar: Array<FloatArray>, chunkBacking: Array<FloatArray>, weight: FloatArray) {
        for (c in 0..1) {
            for (k in 0 until length) {
                guitar[c][k] += weight[k] * chunkGuitar[c][k]
                backing[c][k] += weight[k] * chunkBacking[c][k]
            }
        }
        for (k in 0 until length) weightSum[k] += weight[k]
    }

    /** Entrega as posições de `base` até [until] e anda a janela. */
    fun flush(until: Int) {
        val count = until - base
        var frames = 0
        for (i in 0 until count) {
            val songFrame = base + i - MAX_SHIFT
            if (songFrame !in 0 until song.frames) continue
            for (c in 0..1) {
                outGuitar[2 * frames + c] = guitar[c][i] / weightSum[i] * song.std + song.mean
                outBacking[2 * frames + c] = backing[c][i] / weightSum[i] * song.std + OTHER_SOURCES * song.mean
            }
            frames++
        }
        if (frames > 0) sink.write(outGuitar, outBacking, frames)
        shift(count)
        base = until
    }

    private fun shift(count: Int) {
        for (array in guitar + backing + arrayOf(weightSum)) {
            array.copyInto(array, 0, count, Demucs.SEGMENT)
            array.fill(0f, Demucs.SEGMENT - count, Demucs.SEGMENT)
        }
    }

    companion object {
        const val MAX_SHIFT = Demucs.SAMPLE_RATE / 2
        private const val OTHER_SOURCES = Demucs.SOURCES - 1
    }
}
