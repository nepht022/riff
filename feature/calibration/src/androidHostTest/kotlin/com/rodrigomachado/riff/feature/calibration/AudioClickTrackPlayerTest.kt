package com.rodrigomachado.riff.feature.calibration

import com.rodrigomachado.riff.core.audio.AudioSink
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

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

    /** Saída que anda com o relógio e conta quantas leituras da posição acontecem ao mesmo tempo. */
    private class WatchedSink(private val total: Long) : AudioSink {
        private val readers = java.util.concurrent.atomic.AtomicInteger()
        private val startedAt = System.nanoTime()
        var mostReadersAtOnce = 0

        override fun start() = Unit

        override fun pause() = Unit

        override fun stop() = Unit

        override fun write(buffer: FloatArray, offsetFrames: Int, frames: Int): Int = frames

        override val playedFrames: Long
            get() {
                val now = readers.incrementAndGet()
                synchronized(this) { mostReadersAtOnce = maxOf(mostReadersAtOnce, now) }
                Thread.sleep(15) // a leitura da posição do AudioTrack não é instantânea
                readers.decrementAndGet()
                // Termina em ~600 ms de relógio.
                return minOf(total, (System.nanoTime() - startedAt) / 1_000_000 * total / 600)
            }

        override fun release() = Unit
    }

    @Test
    fun theOutputPositionIsReadByOneCoroutineAtATime() {
        val sink = WatchedSink(total = ClickTrack.render(44_100).size / 2L)
        val player = AudioClickTrackPlayer { sink }

        runBlocking { withTimeout(30_000) { player.play() } }

        // A posição apresentada (PresentedFrames) guarda estado entre leituras: duas ao mesmo tempo a corrompem.
        assertEquals(1, sink.mostReadersAtOnce)
    }

    /** Saída que abre mas não toca (fone desconectado, driver parado): a escrita espera até a saída ser parada. */
    private class BlockedSink : AudioSink {
        @Volatile var stopped = false
        var released = false

        override fun start() = Unit

        override fun pause() = Unit

        override fun stop() {
            stopped = true
        }

        override fun write(buffer: FloatArray, offsetFrames: Int, frames: Int): Int {
            while (!stopped) Thread.sleep(2)
            return 0
        }

        override val playedFrames: Long get() = 0

        override fun release() {
            released = true
        }
    }

    @Test
    fun cancellingTheMeasurementFreesAnOutputStuckWriting() {
        val sink = BlockedSink()
        val player = AudioClickTrackPlayer { sink }

        runBlocking {
            val measuring = async(kotlinx.coroutines.Dispatchers.Default) { player.play() }
            delay(200)
            // Sem a correção, a escrita não volta nunca e o cancelamento espera para sempre.
            withTimeout(5_000) { measuring.cancelAndJoinQuietly() }
        }

        assertTrue(sink.released)
    }

    @Test
    fun anOutputThatStopsWhileWritingFailsTheMeasurement() {
        val sink = BlockedSink()
        val player = AudioClickTrackPlayer { sink }

        runBlocking {
            try {
                withTimeout(30_000) { player.play() }
                fail("a medição deveria falhar")
            } catch (_: IOException) {
                // A tela mostra "Não deu para tocar o metrônomo".
            }
        }

        assertTrue(sink.released)
    }

    private suspend fun kotlinx.coroutines.Deferred<*>.cancelAndJoinQuietly() {
        cancel()
        join()
    }
}
