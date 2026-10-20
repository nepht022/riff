package com.rodrigomachado.riff.core.audio

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.math.pow

class MusicStartTest {
    /** Potência de uma janela de 50 ms a [db] dB. */
    private fun db(db: Double) = 10.0.pow(db / 10)

    private fun windows(vararg parts: Pair<Int, Double>): DoubleArray =
        parts.flatMap { (count, level) -> List(count) { db(level) } }.toDoubleArray()

    @Test
    fun aCountInBeforeTheMusicIsSkipped() {
        // 4 s de contagem: uma batida curta (50 ms) a cada 500 ms, depois a banda
        val countIn = (0 until 80).map { if (it % 10 == 0) db(-40.0) else db(-90.0) }
        val power = (countIn + List(100) { db(-15.0) }).toDoubleArray()

        assertEquals(4_000L, MusicStart.find(power))
    }

    @Test
    fun musicFromTheFirstInstantStartsAtZero() {
        assertEquals(0L, MusicStart.find(windows(100 to -15.0)))
    }

    @Test
    fun aQuietButSteadyIntroIsPartOfTheMusic() {
        assertEquals(1_000L, MusicStart.find(windows(20 to -90.0, 40 to -35.0, 100 to -15.0)))
    }

    @Test
    fun silenceHasNoStart() {
        assertNull(MusicStart.find(windows(100 to -120.0)))
        assertNull(MusicStart.find(DoubleArray(0)))
    }

    private class Tone(
        private val silentFrames: Int,
        private val totalFrames: Int,
        override val durationMs: Long? = null,
    ) : PcmSource {
        override val sampleRate = 1_000
        override val channelCount = 2
        private var at = 0

        override fun read(buffer: FloatArray, maxFrames: Int): Int {
            val frames = minOf(maxFrames, totalFrames - at)
            for (i in 0 until frames) {
                val value = if (at + i < silentFrames) 0f else 0.5f
                buffer[i * 2] = value
                buffer[i * 2 + 1] = value
            }
            at += frames
            return frames
        }

        override fun close() = Unit
    }

    @Test
    fun theFinderDecodesEveryTrackOfTheChart() {
        val tracks = mapOf("guitar.ogg" to Tone(3_000, 10_000), "song.ogg" to Tone(5_000, 10_000))
        val finder = PcmMusicStartFinder { uri -> tracks.getValue(uri) }

        assertEquals(3_000L, runBlocking { finder.analyze(listOf("guitar.ogg", "song.ogg")).musicStartMs })
    }

    /** A duração do áudio do mapa (a faixa mais longa): comparada com a do Spotify, limita o ajuste de sincronia. */
    @Test
    fun theDurationIsTheLongestTrack() {
        val tracks = mapOf(
            "guitar.ogg" to Tone(3_000, 10_000, durationMs = 250_000),
            "song.ogg" to Tone(5_000, 10_000, durationMs = 253_000),
        )
        val finder = PcmMusicStartFinder { uri -> tracks.getValue(uri) }

        assertEquals(253_000L, runBlocking { finder.analyze(listOf("guitar.ogg", "song.ogg")).durationMs })
    }

    @Test
    fun aTrackThatCannotBeDecodedGivesNoStart() {
        val finder = PcmMusicStartFinder { throw IOException("arquivo quebrado") }

        assertNull(runBlocking { finder.analyze(listOf("song.ogg")).musicStartMs })
    }

    @Test
    fun leavingDuringTheAnalysisStopsDecoding() {
        val opened = mutableListOf<String>()
        var reads = 0
        lateinit var job: Job
        val finder = PcmMusicStartFinder { uri ->
            opened += uri
            object : PcmSource {
                override val sampleRate = 1_000
                override val channelCount = 1

                override fun read(buffer: FloatArray, maxFrames: Int): Int {
                    if (++reads == 3) job.cancel() // o jogador saiu da partida
                    buffer.fill(0.5f, 0, maxFrames)
                    return maxFrames
                }

                override fun close() = Unit
            }
        }

        runBlocking {
            job = launch { finder.analyze(listOf("guitar.ogg", "song.ogg")) }
            job.join()
        }

        assertEquals(listOf("guitar.ogg"), opened)
        assertTrue("leu $reads blocos depois de cancelado", reads <= 4)
    }
}
