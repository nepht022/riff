package com.rodrigomachado.riff.core.audio

import com.rodrigomachado.riff.core.chart.sng.ByteArrayRandomRead
import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.math.sqrt

/** O StemSongPlayer comum com o FFmpeg do PC e uma saída falsa que só conta quadros. */
class DesktopStemPlaybackTest {
    private val files = RandomReadOpener { ByteArrayRandomRead(File(it).readBytes()) }
    private fun fixture(name: String): String = File(javaClass.getResource("/audio/$name")!!.toURI()).path

    private class CountingSink : AudioSink {
        var played = 0L
        var lastBlock = FloatArray(0)
        override fun start() = Unit
        override fun pause() = Unit
        override fun stop() = Unit
        override fun write(buffer: FloatArray, offsetFrames: Int, frames: Int): Int {
            lastBlock = buffer.copyOfRange(offsetFrames * 2, (offsetFrames + frames) * 2)
            played += frames
            return frames
        }
        override val playedFrames: Long get() = played
        override fun release() = Unit
    }

    private fun stems(sinks: AudioSinkFactory) = StemSongPlayer(
        guitarUri = fixture("tom_440_44k_mono.ogg"),
        backingUris = listOf(fixture("tom_440_48k_estereo.wav")),
        opener = { FfmpegPcmSource.open(it, files) },
        sinks = sinks,
        startThread = false,
    )

    private fun rms(block: FloatArray) = sqrt(block.sumOf { (it * it).toDouble() } / block.size)

    @Test
    fun separatedStemsPlayTogetherAndTheGuitarCuts() = runBlocking {
        val sink = CountingSink()
        val player = stems { sink }
        player.prepare("")
        player.play()
        player.renderOnce()
        val both = rms(sink.lastBlock)

        player.setGuitarAudible(false)
        repeat(3) { player.renderOnce() }
        val backingOnly = rms(sink.lastBlock)
        player.release()

        assertTrue("guitarra + resto $both, só o resto $backingOnly", backingOnly < both * 0.8)
    }

    @Test
    fun aSingleSongCannotBeSilencedByAMiss() {
        val song = DesktopPlayers.songPlayers(files).create()

        assertFalse(song is GuitarTrackControl)
        song.release()
    }

    @Test
    fun thePositionNeverGoesBack() = runBlocking {
        // Cada reposicionamento abre uma saída nova, que conta do zero (como a de verdade).
        val player = stems { CountingSink() }
        player.prepare("")
        player.play()
        var last = player.positionMs
        repeat(10) {
            player.renderOnce()
            assertTrue(player.positionMs >= last)
            last = player.positionMs
        }
        player.pause()
        player.seekTo(500)

        assertEquals(500.0, player.positionMs.toDouble(), 25.0)
        player.release()
    }

    @Test
    fun aMissingOutputWhenSeekingDoesNotCrash() = runBlocking {
        var opened = 0
        val player = stems { if (opened++ == 0) CountingSink() else throw IOException("sem saída de som") }
        player.prepare("")
        player.pause()

        player.seekTo(500) // a saída nova não abre: a música fica onde estava, sem derrubar a partida
        player.release()
    }
}
