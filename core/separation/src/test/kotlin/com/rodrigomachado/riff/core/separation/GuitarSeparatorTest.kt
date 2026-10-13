package com.rodrigomachado.riff.core.separation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/** Música em memória, como o SongPcmLoader entrega. */
class ArraySong(private val left: FloatArray, private val right: FloatArray) : SongPcm {
    override val frames = left.size
    private val mono = FloatArray(frames) { (left[it] + right[it]) / 2 }
    override val mean = mono.average().toFloat()
    override val std = sqrt(mono.sumOf { ((it - mean) * (it - mean)).toDouble() } / (frames - 1)).toFloat()
    override fun sample(channel: Int, frame: Int) = if (channel == 0) left[frame] else right[frame]
}

class CollectingSink : StemSink {
    val guitar = ArrayList<Float>()
    val backing = ArrayList<Float>()
    override fun write(guitar: FloatArray, backing: FloatArray, frames: Int) {
        for (i in 0 until 2 * frames) {
            this.guitar += guitar[i]
            this.backing += backing[i]
        }
    }
    fun channel(values: List<Float>, c: Int) = FloatArray(values.size / 2) { values[2 * it + c] }
}

/** Devolve a entrada como guitarra pelo ramo do tempo ([viaSpectrum] = pelo espectro); as outras fontes zeradas. */
class EchoModel(private val viaSpectrum: Boolean = false) : DemucsModel {
    var calls = 0
    override fun run(mix: FloatArray, spec: FloatArray): DemucsOutput {
        calls++
        val outSpec = FloatArray(Demucs.SOURCES * spec.size)
        val outMix = FloatArray(Demucs.SOURCES * mix.size)
        if (viaSpectrum) {
            spec.copyInto(outSpec, Demucs.GUITAR * spec.size)
        } else {
            mix.copyInto(outMix, Demucs.GUITAR * mix.size)
        }
        return DemucsOutput(outSpec, outMix)
    }
    override fun close() = Unit
}

class GuitarSeparatorTest {
    private fun tone(frames: Int, f: Double, phase: Double = 0.0) =
        FloatArray(frames) { (0.3 * sin(2 * Math.PI * f * it / Demucs.SAMPLE_RATE + phase) + 0.01).toFloat() }

    @Test
    fun aGuitarOnlyModelGivesTheSongBackAcrossSegments() = runTest {
        val left = tone(400_000, 220.0)
        val right = tone(400_000, 330.0, 1.0)
        val sink = CollectingSink()

        GuitarSeparator(EchoModel()).separate(ArraySong(left, right), sink)

        assertArrayEquals(left, sink.channel(sink.guitar, 0), 1e-4f)
        assertArrayEquals(right, sink.channel(sink.guitar, 1), 1e-4f)
    }

    @Test
    fun theSpectrumBranchAlsoRoundTrips() = runTest {
        val left = tone(200_000, 440.0)
        val sink = CollectingSink()

        GuitarSeparator(EchoModel(viaSpectrum = true)).separate(ArraySong(left, left.copyOf()), sink)

        assertArrayEquals(left, sink.channel(sink.guitar, 0), 1e-3f)
    }

    @Test
    fun theRestGetsTheMeanOfTheOtherFiveSources() = runTest {
        val left = tone(50_000, 220.0)
        val song = ArraySong(left, left.copyOf())
        val sink = CollectingSink()

        GuitarSeparator(EchoModel()).separate(song, sink)

        assertTrue(sink.backing.all { abs(it - 5 * song.mean) < 1e-4f })
    }

    @Test
    fun aSongShorterThanOneSegmentIsOneChunk() = runTest {
        val left = tone(2 * Demucs.SAMPLE_RATE, 220.0)
        val model = EchoModel()
        val sink = CollectingSink()

        GuitarSeparator(model).separate(ArraySong(left, left.copyOf()), sink)

        assertEquals(1, model.calls)
        assertArrayEquals(left, sink.channel(sink.guitar, 0), 1e-4f)
    }

    @Test
    fun silenceDoesNotTurnIntoNaN() = runTest {
        val sink = CollectingSink()

        GuitarSeparator(EchoModel()).separate(ArraySong(FloatArray(100_000), FloatArray(100_000)), sink)

        assertEquals(200_000, sink.guitar.size)
        assertTrue(sink.guitar.all { it == 0f } && sink.backing.all { it == 0f })
    }

    @Test
    fun reportsEachChunkAndStopsAtTheCheckpoint() = runTest {
        val left = tone(400_000, 220.0)
        val reported = mutableListOf<Pair<Int, Int>>()
        val separator = GuitarSeparator(EchoModel())

        val error = runCatching {
            separator.separate(
                ArraySong(left, left.copyOf()),
                CollectingSink(),
                onChunk = { done, total -> reported += done to total },
                checkpoint = { if (reported.size == 2) throw CancellationException("parou") },
            )
        }.exceptionOrNull()

        assertEquals(listOf(1 to 4, 2 to 4), reported)
        assertTrue(error is CancellationException)
        assertEquals(4, chunkCount(400_000))
    }
}
