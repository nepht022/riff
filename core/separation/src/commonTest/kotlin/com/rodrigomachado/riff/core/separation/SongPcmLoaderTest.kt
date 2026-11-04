package com.rodrigomachado.riff.core.separation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SongPcmLoaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun aStereoSongKeepsItsChannelsAndStats() {
        val interleaved = FloatArray(2 * 10_000) { if (it % 2 == 0) 0.5f else -0.25f }
        val loader = SongPcmLoader { ArrayPcmSource(interleaved, 44_100, 2) }

        loader.load("x", tmp.root).use { song ->
            assertEquals(10_000, song.frames)
            assertEquals(0.5f, song.sample(0, 1234))
            assertEquals(-0.25f, song.sample(1, 1234))
            assertEquals(0.125f, song.mean, 1e-6f)
            assertEquals(0f, song.std, 1e-6f)
        }
        assertEquals(0, tmp.root.listFiles()!!.size) // o temporário sai no close
    }

    @Test
    fun aMono48kSongBecomesStereoAt44k() {
        val mono = FloatArray(48_000) { (it % 48) / 48f }
        val loader = SongPcmLoader { ArrayPcmSource(mono, 48_000, 1) }

        loader.load("x", tmp.root).use { song ->
            assertEquals(44_100.0, song.frames.toDouble(), 50.0)
            assertEquals(song.sample(0, 500), song.sample(1, 500))
        }
    }

    @Test
    fun moreThanTwoChannelsKeepTheFirstTwo() {
        val six = FloatArray(6 * 100) { (it % 6).toFloat() }
        SongPcmLoader { ArrayPcmSource(six, 44_100, 6) }.load("x", tmp.root).use { song ->
            assertEquals(0f, song.sample(0, 50))
            assertEquals(1f, song.sample(1, 50))
            assertFalse(song.std.isNaN())
        }
    }

    @Test
    fun cancellingDuringTheDecodeStopsAndCleansUp() {
        var blocks = 0
        val long = FloatArray(2 * 500_000)
        val loader = SongPcmLoader { ArrayPcmSource(long, 48_000, 2) }

        val error = runCatching { loader.load("x", tmp.root) { ++blocks < 3 } }.exceptionOrNull()

        assertTrue(error is kotlinx.coroutines.CancellationException)
        assertEquals(0, tmp.root.listFiles()!!.size)
    }
}
