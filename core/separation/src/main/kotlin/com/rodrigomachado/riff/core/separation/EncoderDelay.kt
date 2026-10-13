package com.rodrigomachado.riff.core.separation

import android.util.Log
import com.rodrigomachado.riff.core.audio.PcmSourceOpener
import java.io.File
import kotlin.math.abs

/**
 * O codificador AAC do Android começa cada arquivo com amostras de "aquecimento" (no S25, 2048 = 46 ms) e o
 * `.m4a` do MediaMuxer não diz quantas: decodificado, tudo sai atrasado em relação ao áudio original do mapa.
 * Mede-se o atraso deste aparelho codificando e decodificando um clique pelo mesmo caminho do jogo.
 */
object EncoderDelay {
    const val CLICK_AT = 4410
    private const val PROBE_FRAMES = 22_050
    private const val READ_FRAMES = 30_000
    private const val TAG = "EncoderDelay"
    private const val BLOCK = 4096

    /** Atraso (em quadros) entre onde o clique foi gravado e onde ele saiu na decodificação. */
    fun lagOf(decoded: FloatArray): Int {
        var peak = 0
        for (i in decoded.indices) if (abs(decoded[i]) > abs(decoded[peak])) peak = i
        return (peak - CLICK_AT).coerceAtLeast(0)
    }

    /** Codifica um clique num `.m4a` temporário e o decodifica com [decoder]; 0 se algo falhar. */
    @Suppress("TooGenericExceptionCaught") // sem a medida, grava sem compensar (como antes)
    fun measure(scratch: File, decoder: PcmSourceOpener): Int {
        val file = File.createTempFile("atraso", ".m4a", scratch)
        return try {
            val click = FloatArray(2 * PROBE_FRAMES)
            click[2 * CLICK_AT] = 1f
            click[2 * CLICK_AT + 1] = 1f
            AacFile(file).apply {
                write(click, PROBE_FRAMES)
                finish()
            }
            decoder.open("file://" + file.invariantSeparatorsPath).use { source ->
                val channels = source.channelCount
                val block = FloatArray(BLOCK * channels)
                val left = FloatArray(READ_FRAMES)
                var read = 0
                while (read < READ_FRAMES) {
                    val n = source.read(block, minOf(BLOCK, READ_FRAMES - read))
                    if (n == 0) break
                    for (k in 0 until n) left[read + k] = block[k * channels]
                    read += n
                }
                lagOf(left).also { Log.i(TAG, "atraso do codificador AAC: $it quadros") }
            }
        } catch (e: Exception) {
            Log.w(TAG, "não deu para medir o atraso do codificador", e)
            0
        } finally {
            file.delete()
        }
    }
}

/** Descarta os primeiros [skipFrames] quadros (o atraso do codificador) antes de gravar. */
class SkippingStemSink(private val inner: StemSink, private var skipFrames: Int) : ClosableStemSink {
    override fun write(guitar: FloatArray, backing: FloatArray, frames: Int) {
        val skip = minOf(skipFrames, frames)
        skipFrames -= skip
        if (skip == frames) return
        if (skip == 0) {
            inner.write(guitar, backing, frames)
            return
        }
        inner.write(guitar.copyOfRange(2 * skip, 2 * frames), backing.copyOfRange(2 * skip, 2 * frames), frames - skip)
    }

    override fun finish() {
        (inner as? ClosableStemSink)?.finish()
    }

    override fun abort() {
        (inner as? ClosableStemSink)?.abort()
    }
}
