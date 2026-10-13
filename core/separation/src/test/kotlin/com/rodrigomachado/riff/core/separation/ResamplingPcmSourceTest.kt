package com.rodrigomachado.riff.core.separation

import com.rodrigomachado.riff.core.audio.PcmSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/** Fonte em memória, entregue em blocos pequenos (como o MediaCodec). */
class ArrayPcmSource(
    private val interleaved: FloatArray,
    override val sampleRate: Int,
    override val channelCount: Int,
) : PcmSource {
    private var position = 0
    override fun read(buffer: FloatArray, maxFrames: Int): Int {
        val frames = minOf(maxFrames, 1000, (interleaved.size - position) / channelCount)
        interleaved.copyInto(buffer, 0, position, position + frames * channelCount)
        position += frames * channelCount
        return frames
    }
    override fun close() = Unit
}

class ResamplingPcmSourceTest {
    @Test
    fun a48kToneComesOutAt44kWithTheSamePitch() {
        val input = FloatArray(48_000) { (0.5 * sin(2 * Math.PI * 1000 * it / 48_000)).toFloat() }
        val source = ResamplingPcmSource(ArrayPcmSource(input, 48_000, 1), 44_100)
        val out = ArrayList<Float>()
        val buffer = FloatArray(4096)
        while (true) {
            val n = source.read(buffer, 4096)
            if (n == 0) break
            for (i in 0 until n) out += buffer[i]
        }

        assertEquals(44_100, source.sampleRate)
        assertEquals(44_100.0, out.size.toDouble(), 50.0)
        // 1 kHz a 44,1 kHz: compara o meio do sinal com a senoide esperada.
        for (i in 1000 until 43_000) {
            assertEquals(0.5 * sin(2 * Math.PI * 1000 * i / 44_100), out[i].toDouble(), 0.01)
        }
    }

    @Test
    fun theSameRatePassesThrough() {
        val input = FloatArray(2000) { it / 2000f }
        val source = ResamplingPcmSource(ArrayPcmSource(input, 44_100, 2), 44_100)
        val buffer = FloatArray(2000)

        assertEquals(1000, source.read(buffer, 1000))
        assertEquals(input[1999], buffer[1999])
    }

    @Test
    fun aMinuteOfStereoResamplesQuickly() {
        val input = FloatArray(2 * 48_000 * 60) { (0.3 * sin(it * 0.01)).toFloat() }
        val source = ResamplingPcmSource(ArrayPcmSource(input, 48_000, 2), 44_100)
        val buffer = FloatArray(2 * 4096)

        val started = System.nanoTime()
        while (source.read(buffer, 4096) > 0) Unit
        val seconds = (System.nanoTime() - started) / 1e9

        // Uma música de 4 min decodificava em ~30 s no S25 só por causa disto; o limite é folgado para o PC.
        assertTrue("levou $seconds s", seconds < 4.0)
    }
}
