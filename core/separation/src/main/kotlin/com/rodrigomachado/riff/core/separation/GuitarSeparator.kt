package com.rodrigomachado.riff.core.separation

import kotlin.math.min

/** O áudio a separar, em 44,1 kHz estéreo, com a média e o desvio do mono (a normalização do Demucs). */
interface SongPcm {
    val frames: Int
    val mean: Float
    val std: Float

    fun sample(channel: Int, frame: Int): Float
}

/** Recebe a guitarra e o resto em blocos, em ordem, estéreo intercalado. */
fun interface StemSink {
    fun write(guitar: FloatArray, backing: FloatArray, frames: Int)
}

private const val STRIDE = Demucs.SEGMENT * 3 / 4
private const val HALF = Demucs.SEGMENT / 2

/** Quantos trechos a separação de uma música de [frames] amostras roda (o progresso conta por eles). */
fun chunkCount(frames: Int): Int = (frames + StemAccumulator.MAX_SHIFT + STRIDE - 1) / STRIDE

/**
 * A separação do demucs.onnx (`apply_model` do Demucs, MIT) com trechos de 4 s: normaliza pelo mono, desloca 0,5 s,
 * roda trechos com 25 % de sobreposição e soma com peso em triângulo. Guarda só a guitarra e o resto (soma das outras
 * cinco fontes). [checkpoint] roda antes de cada trecho: é onde a separação pausa ou é cancelada.
 */
class GuitarSeparator(private val model: DemucsModel) {
    private val stft = DemucsStft(Demucs.SEGMENT)
    private val plane = Demucs.BINS * Demucs.FRAMES
    private val weight =
        FloatArray(Demucs.SEGMENT) { k -> (if (k < HALF) k + 1 else Demucs.SEGMENT - k).toFloat() / HALF }
    private val mixIn = FloatArray(2 * Demucs.SEGMENT)
    private val specIn = FloatArray(4 * plane)
    private val restSpec = FloatArray(4 * plane)
    private val channel = FloatArray(Demucs.SEGMENT)
    private val wave = FloatArray(Demucs.SEGMENT)
    private val chunkGuitar = Array(2) { FloatArray(Demucs.SEGMENT) }
    private val chunkBacking = Array(2) { FloatArray(Demucs.SEGMENT) }

    suspend fun separate(
        song: SongPcm,
        sink: StemSink,
        onChunk: (done: Int, total: Int) -> Unit = { _, _ -> },
        checkpoint: suspend () -> Unit = {},
    ) {
        val shiftedLength = song.frames + StemAccumulator.MAX_SHIFT
        val total = chunkCount(song.frames)
        val accumulator = StemAccumulator(song, sink)
        for (index in 0 until total) {
            checkpoint()
            val offset = index * STRIDE
            val length = min(Demucs.SEGMENT, shiftedLength - offset)
            runChunk(song, offset, length)
            accumulator.add(length, chunkGuitar, chunkBacking, weight)
            accumulator.flush(if (index + 1 < total) offset + STRIDE else shiftedLength)
            onChunk(index + 1, total)
        }
    }

    /** Roda o modelo no trecho e deixa em [chunkGuitar]/[chunkBacking] as [length] amostras do centro. */
    private fun runChunk(song: SongPcm, offset: Int, length: Int) {
        val delta = Demucs.SEGMENT - length
        val start = offset - delta / 2 // o trecho curto do fim pega áudio de antes, como o TensorChunk.padded
        val scale = if (song.std > 0f) 1f / song.std else 1f
        for (c in 0..1) {
            for (k in 0 until Demucs.SEGMENT) {
                val frame = start + k - StemAccumulator.MAX_SHIFT
                channel[k] = if (frame in 0 until song.frames) (song.sample(c, frame) - song.mean) * scale else 0f
            }
            channel.copyInto(mixIn, c * Demucs.SEGMENT)
            stft.forward(channel, specIn, offset = c * 2 * plane)
        }
        val out = model.run(mixIn, specIn)
        sumOtherSources(out.spec)
        for (c in 0..1) {
            waveOf(out.spec, (Demucs.GUITAR * 4 + c * 2) * plane, out.mix, Demucs.GUITAR, c)
            wave.copyInto(chunkGuitar[c], 0, delta / 2, delta / 2 + length)
            waveOf(restSpec, c * 2 * plane, out.mix, source = OTHERS, c)
            wave.copyInto(chunkBacking[c], 0, delta / 2, delta / 2 + length)
        }
    }

    private fun sumOtherSources(spec: FloatArray) {
        restSpec.fill(0f)
        for (s in 0 until Demucs.SOURCES) {
            if (s == Demucs.GUITAR) continue
            val from = s * 4 * plane
            for (i in restSpec.indices) restSpec[i] += spec[from + i]
        }
    }

    /** Onda de um canal: iSTFT do espectro em [specOffset] mais o ramo do tempo ([source] [OTHERS] = as outras 5). */
    private fun waveOf(spec: FloatArray, specOffset: Int, mix: FloatArray, source: Int, c: Int) {
        stft.inverse(spec, specOffset, wave)
        for (s in 0 until Demucs.SOURCES) {
            if (!includes(source, s)) continue
            val from = (s * 2 + c) * Demucs.SEGMENT
            for (k in 0 until Demucs.SEGMENT) wave[k] += mix[from + k]
        }
    }

    private fun includes(source: Int, s: Int) = if (source == OTHERS) s != Demucs.GUITAR else s == source

    private companion object {
        const val OTHERS = -1
    }
}
