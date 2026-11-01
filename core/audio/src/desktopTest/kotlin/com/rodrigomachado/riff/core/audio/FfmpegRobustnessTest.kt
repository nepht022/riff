package com.rodrigomachado.riff.core.audio

import com.rodrigomachado.riff.core.chart.sng.ByteArrayRandomRead
import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Arquivos do mundo real: corrompidos no meio, cortados, que mudam de formato, e o reposicionar em cada formato. */
class FfmpegRobustnessTest {
    private val files = RandomReadOpener { ByteArrayRandomRead(File(it).readBytes()) }

    private fun fixture(name: String): String = File(javaClass.getResource("/audio/$name")!!.toURI()).path

    private fun decode(name: String, seekFrame: Long? = null): FloatArray =
        FfmpegPcmSource.open(fixture(name), files).use { source ->
            seekFrame?.let(source::seekTo)
            val out = ArrayList<Float>()
            val block = FloatArray(BLOCK * 2)
            while (true) {
                val frames = source.read(block, BLOCK)
                if (frames == 0) break
                for (i in 0 until frames * 2) out += block[i]
            }
            out.toFloatArray()
        }

    @Test
    fun aCorruptedMiddleDoesNotStopTheSong() {
        val frames = decode("quebrado_no_meio.mp3").size / 2

        assertTrue("$frames quadros", frames > 40_000)
    }

    /** Os bipes (3 s) cortados em 70%: toca o que tem e acaba, sem erro. */
    @Test
    fun aTruncatedFileEndsEarlyWithoutAnError() {
        val frames = decode("cortado.ogg").size / 2

        assertTrue("$frames quadros", frames in 24_000..140_000)
    }

    /**
     * Um mp3 mono emendado num estéreo: o mesmo fluxo muda de canais no meio, e tudo segue em 48 kHz estéreo. Os dois
     * pedaços trazem o atraso do codificador como silêncio: até ~5 mil quadros a mais que os 2 s.
     */
    @Test
    fun aStreamThatChangesFormatKeepsComingOutAt48kStereo() {
        val frames = decode("mono_depois_estereo.mp3").size / 2

        assertTrue("$frames quadros", frames in 96_000..101_000)
    }

    /** Depois de reposicionar, o próximo bipe cai no mesmo quadro que na decodificação desde o começo (±2 ms). */
    @Test
    fun seekingLandsOnTheSameSampleInLossyFormats() {
        for (name in listOf("bipes.mp3", "bipes.ogg", "bipes.opus")) {
            val target = 54_240L // 1,13 s: entre dois bipes
            val expected = firstOnset(decode(name), from = target.toInt())
            val afterSeek = target + firstOnset(decode(name, seekFrame = target), from = 0)

            assertTrue("$name: esperado $expected, depois de reposicionar $afterSeek", abs(afterSeek - expected) <= 96)
        }
    }

    /** O primeiro quadro, a partir de [from], em que o som sai do silêncio (canal esquerdo). */
    private fun firstOnset(samples: FloatArray, from: Int): Int {
        var quiet = 0
        for (frame in from until samples.size / 2) {
            val loud = abs(samples[frame * 2]) > 0.1f
            if (loud && quiet >= QUIET_FRAMES) return frame
            quiet = if (loud) 0 else quiet + 1
        }
        error("sem bipe depois de $from")
    }

    private companion object {
        const val BLOCK = 1_024
        const val QUIET_FRAMES = 480
    }
}
