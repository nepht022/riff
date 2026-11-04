package com.rodrigomachado.riff.core.separation

import com.rodrigomachado.riff.core.audio.FfmpegPcmSource
import com.rodrigomachado.riff.core.audio.PcmSource
import com.rodrigomachado.riff.core.data.SEPARATED_BACKING
import com.rodrigomachado.riff.core.data.SEPARATED_GUITAR
import com.rodrigomachado.riff.core.data.folder.FileRandomReadOpener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class FfmpegStemSinkTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val randomRead = FileRandomReadOpener()

    /** O que o jogo do PC ouve: o `.m4a` decodificado pelo leitor do Riff (48 kHz estéreo). */
    private fun decode(file: File): Pair<FloatArray, Long?> = FfmpegPcmSource.open(file.path, randomRead).use {
        decodeAll(it) to it.durationMs
    }

    private fun decodeAll(source: PcmSource): FloatArray {
        val out = ArrayList<Float>()
        val block = FloatArray(BLOCK * source.channelCount)
        while (true) {
            val frames = source.read(block, BLOCK)
            if (frames == 0) break
            for (i in 0 until frames * source.channelCount) out += block[i]
        }
        return out.toFloatArray()
    }

    /** Grava [guitar] e [backing] (intercalados, 44,1 kHz) em blocos de tamanhos variados, como o separador. */
    private fun record(dir: File, guitar: FloatArray, backing: FloatArray) {
        val sink = DesktopSeparation.stemSinks(tmp.newFolder("rascunho")).create(dir)
        val frames = guitar.size / 2
        var done = 0
        var step = 0
        while (done < frames) {
            val n = minOf(BLOCKS[step++ % BLOCKS.size], frames - done)
            sink.write(guitar.copyOfRange(2 * done, 2 * (done + n)), backing.copyOfRange(2 * done, 2 * (done + n)), n)
            done += n
        }
        sink.finish()
    }

    private fun peakFrame(stereo: FloatArray): Int {
        var peak = 0
        for (i in 0 until stereo.size / 2) if (abs(stereo[2 * i]) > abs(stereo[2 * peak])) peak = i
        return peak
    }

    @Test
    fun aClickComesOutWhereItWentIn() {
        val frames = 3 * Demucs.SAMPLE_RATE
        val guitar = FloatArray(2 * frames).apply { fill(0.9f, 2 * GUITAR_CLICK, 2 * GUITAR_CLICK + 2) }
        val backing = FloatArray(2 * frames).apply { fill(0.9f, 2 * BACKING_CLICK, 2 * BACKING_CLICK + 2) }
        val dir = tmp.newFolder("separacao")

        record(dir, guitar, backing)

        for ((name, click) in listOf(SEPARATED_GUITAR to GUITAR_CLICK, SEPARATED_BACKING to BACKING_CLICK)) {
            val (decoded, _) = decode(File(dir, name))
            val expectedMs = click * MS_PER_S / Demucs.SAMPLE_RATE
            val gotMs = peakFrame(decoded) * MS_PER_S / OUT_RATE
            assertEquals("$name: o clique em $expectedMs ms saiu em $gotMs ms", expectedMs, gotMs, 1.0)
        }
    }

    @Test
    fun aToneKeepsItsLengthAndLevel() {
        val frames = 5 * Demucs.SAMPLE_RATE / 2 // 2,5 s
        val tone = FloatArray(2 * frames) { (0.5 * sin(2 * Math.PI * 440 * (it / 2) / Demucs.SAMPLE_RATE)).toFloat() }
        val dir = tmp.newFolder("separacao")

        record(dir, tone, tone.copyOf())

        for (name in listOf(SEPARATED_GUITAR, SEPARATED_BACKING)) {
            val (decoded, durationMs) = decode(File(dir, name))
            val decodedMs = decoded.size / 2 * MS_PER_S / OUT_RATE
            assertEquals("$name: duração do arquivo", 2_500.0, durationMs!!.toDouble(), 1.0)
            // O leitor do PC entrega o último quadro AAC inteiro (1024 amostras, ~23 ms): o fim pode sobrar.
            assertTrue("$name: $decodedMs ms decodificados", decodedMs in 2_499.0..2_500.0 + AAC_FRAME_MS)
            val rms = sqrt(decoded.copyOfRange(OUT_RATE / 2, 2 * OUT_RATE).map { it * it }.average())
            assertEquals("$name: nível", 0.5 / sqrt(2.0), rms, 0.02)
        }
    }

    @Test
    fun abortingLeavesNoOpenFileBehind() {
        val dir = tmp.newFolder("separacao")
        val sink = DesktopSeparation.stemSinks(tmp.newFolder("rascunho")).create(dir)
        sink.write(FloatArray(2 * 10_000) { 0.1f }, FloatArray(2 * 10_000), 10_000)

        sink.abort()

        // No Windows, um arquivo ainda aberto não pode ser apagado.
        assertTrue(dir.deleteRecursively())
    }

    private companion object {
        const val BLOCK = 4096
        const val OUT_RATE = 48_000
        const val MS_PER_S = 1_000.0
        const val AAC_FRAME_MS = 1024 * MS_PER_S / 44_100
        const val GUITAR_CLICK = 44_100 + 123
        const val BACKING_CLICK = 88_200 + 7
        val BLOCKS = intArrayOf(1000, 4410, 777, 44_100, 3)
    }
}
