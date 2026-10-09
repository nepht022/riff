package com.rodrigomachado.riff.feature.calibration

import com.rodrigomachado.riff.core.audio.AudioSink
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioClickTrackPlayerTest {
    /** Saída que aceita tudo e para de andar perto do fim: o sistema às vezes segura o último pedaço. */
    private class StuckSink(private val stuckAt: Long) : AudioSink {
        var released = false

        override fun start() = Unit

        override fun pause() = Unit

        override fun stop() = Unit

        override fun write(buffer: FloatArray, offsetFrames: Int, frames: Int): Int = frames

        override val playedFrames: Long get() = stuckAt

        override fun release() {
            released = true
        }
    }

    @Test
    fun anOutputThatStopsShortOfTheEndDoesNotHangTheMeasurement() {
        val sink = StuckSink(stuckAt = ClickTrack.clickFrames(44_100).last() - 1_000)
        val player = AudioClickTrackPlayer { sink }

        // Folga para a máquina carregada (suíte em paralelo); sem a correção, o play() não voltaria nunca.
        runBlocking { withTimeout(30_000) { player.play() } }

        assertTrue(sink.released)
    }
}
