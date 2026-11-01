package com.rodrigomachado.riff.core.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** O contrato da thread de áudio com faixas que falham, no código comum (aqui com fontes falsas). */
class StemSongPlayerFailureTest {
    private class NullSink : AudioSink {
        override fun start() = Unit
        override fun pause() = Unit
        override fun stop() = Unit
        override fun write(buffer: FloatArray, offsetFrames: Int, frames: Int): Int = frames
        override val playedFrames: Long get() = 0
        override fun release() = Unit
    }

    /** Toca [failAfter] blocos de silêncio e depois lança [error]. */
    private class FailingSource(private val failAfter: Int, private val error: Exception) : PcmSource {
        var reads = 0
        override val sampleRate = 48_000
        override val channelCount = 2
        override fun read(buffer: FloatArray, maxFrames: Int): Int {
            if (reads++ >= failAfter) throw error
            return maxFrames
        }
        override fun seekTo(frame: Long) = Unit
        override fun close() = Unit
    }

    /** Uma faixa que quebra no meio (IOException, como o FFmpeg) vira silêncio, e a música chega ao fim. */
    @Test
    fun aTrackThatBreaksMidSongEndsInsteadOfKillingTheAudioThread() = runBlocking {
        val player = StemSongPlayer(
            guitarUri = "g",
            backingUris = emptyList(),
            opener = { FailingSource(failAfter = 3, error = IOException("pacote inválido")) },
            sinks = { NullSink() },
            startThread = false,
        )
        player.prepare("")
        player.play()

        var rounds = 0
        while (player.renderOnce()) check(++rounds < 100) { "a música não acabou" }
        player.release()
    }

    /** Fechar enquanto outra thread reposiciona espera o reposicionamento (no PC, fechar no meio derruba o app). */
    @Test
    fun releasingDuringASeekWaitsForIt() = runBlocking {
        val seeking = CountDownLatch(1)
        val letGo = CountDownLatch(1)
        var closedWhileSeeking = false
        var inSeek = false
        val source = object : PcmSource {
            override val sampleRate = 48_000
            override val channelCount = 2
            override fun read(buffer: FloatArray, maxFrames: Int) = maxFrames
            override fun seekTo(frame: Long) {
                inSeek = true
                seeking.countDown()
                letGo.await(5, TimeUnit.SECONDS)
                inSeek = false
            }
            override fun close() {
                if (inSeek) closedWhileSeeking = true
            }
        }
        val player = StemSongPlayer("g", emptyList(), { source }, { NullSink() }, startThread = false)
        player.prepare("")
        player.pause()

        val seeker = Thread { player.seekTo(500) }.apply { start() }
        assertTrue(seeking.await(5, TimeUnit.SECONDS))
        val releaser = Thread { player.release() }.apply { start() }
        Thread.sleep(200)
        letGo.countDown()
        seeker.join(5_000)
        releaser.join(5_000)

        assertFalse(closedWhileSeeking)
    }
}
