package com.rodrigomachado.riff.core.separation

import com.rodrigomachado.riff.core.audio.PcmSource
import com.rodrigomachado.riff.core.audio.PcmSourceOpener
import kotlinx.coroutines.CancellationException
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * A música decodificada num arquivo temporário (fora da heap do app), lida por uma janela de [WINDOW] quadros: a
 * separação lê um trecho de 4 s por vez. Não é mapeada em memória porque o Windows (testes) não apaga arquivo mapeado.
 */
class FileSong internal constructor(
    private val file: File,
    override val frames: Int,
    override val mean: Float,
    override val std: Float,
) : SongPcmHandle {
    private val access = RandomAccessFile(file, "r")
    private val bytes = ByteBuffer.allocate(WINDOW * BYTES_PER_FRAME).order(ByteOrder.LITTLE_ENDIAN)
    private val window = FloatArray(WINDOW * 2)
    private var windowStart = -1
    private var windowFrames = 0

    override fun sample(channel: Int, frame: Int): Float {
        if (frame < windowStart || frame >= windowStart + windowFrames) load(frame)
        return window[2 * (frame - windowStart) + channel]
    }

    private fun load(frame: Int) {
        windowStart = frame
        windowFrames = minOf(WINDOW, frames - frame)
        bytes.clear().limit(windowFrames * BYTES_PER_FRAME)
        access.channel.read(bytes, frame.toLong() * BYTES_PER_FRAME)
        bytes.flip()
        bytes.asFloatBuffer().get(window, 0, windowFrames * 2)
    }

    override fun close() {
        access.close()
        file.delete()
    }

    private companion object {
        const val WINDOW = 2 * Demucs.SEGMENT
        const val BYTES_PER_FRAME = 8
    }
}

/** Decodifica a música inteira para 44,1 kHz estéreo e calcula a média e o desvio do mono (Welford). */
class SongPcmLoader(private val opener: PcmSourceOpener) {
    fun load(uri: String, scratch: File, keepGoing: () -> Boolean = { true }): FileSong {
        val file = File.createTempFile("musica", ".f32", scratch)
        val stats = MonoStats()
        try {
            ResamplingPcmSource(opener.open(uri), Demucs.SAMPLE_RATE).use { source ->
                DataOutputStream(BufferedOutputStream(file.outputStream(), BUFFER)).use { out ->
                    copyStereo(source, out, stats, keepGoing)
                }
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            file.delete()
            throw e
        }
        return FileSong(file, stats.count.toInt(), stats.mean.toFloat(), stats.std().toFloat())
    }

    private fun copyStereo(source: PcmSource, out: DataOutputStream, stats: MonoStats, keepGoing: () -> Boolean) {
        val channels = source.channelCount
        val buffer = FloatArray(BLOCK * channels)
        while (true) {
            if (!keepGoing()) throw CancellationException("separação cancelada durante a decodificação")
            val n = source.read(buffer, BLOCK)
            if (n == 0) break
            for (k in 0 until n) {
                val left = buffer[k * channels]
                val right = if (channels > 1) buffer[k * channels + 1] else left
                out.writeInt(Integer.reverseBytes(left.toRawBits()))
                out.writeInt(Integer.reverseBytes(right.toRawBits()))
                stats.add((left + right) / 2.0)
            }
        }
    }

    private class MonoStats {
        var count = 0L
        var mean = 0.0
        private var m2 = 0.0

        fun add(x: Double) {
            count++
            val delta = x - mean
            mean += delta / count
            m2 += delta * (x - mean)
        }

        fun std() = if (count > 1) sqrt(m2 / (count - 1)) else 0.0
    }

    private companion object {
        const val BLOCK = 4096
        const val BUFFER = 1 shl 16
    }
}
