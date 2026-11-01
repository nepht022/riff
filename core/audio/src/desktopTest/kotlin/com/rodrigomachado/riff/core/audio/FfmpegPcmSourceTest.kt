package com.rodrigomachado.riff.core.audio

import com.rodrigomachado.riff.core.chart.sng.ByteArrayRandomRead
import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
import com.rodrigomachado.riff.core.chart.sng.SngRef
import com.rodrigomachado.riff.core.chart.sng.SngWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class FfmpegPcmSourceTest {
    @get:Rule
    val temp = TemporaryFolder()

    /** No PC, os arquivos soltos são lidos pelo caminho; só as entradas de `.sng` passam por este abridor. */
    private val files = RandomReadOpener { ByteArrayRandomRead(File(it).readBytes()) }

    private fun fixture(name: String): String = File(javaClass.getResource("/audio/$name")!!.toURI()).path

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

    @Test
    fun everyFormatComesOutAt48kStereo() {
        for (name in TONES) {
            FfmpegPcmSource.open(fixture(name), files).use { source ->
                assertEquals(name, 48_000, source.sampleRate)
                assertEquals(name, 2, source.channelCount)
                val frames = decodeAll(source).size / 2
                assertTrue("$name: $frames quadros", frames in 47_000..49_500)
            }
        }
    }

    @Test
    fun theToneIsStill440Hz() {
        val samples = FfmpegPcmSource.open(fixture("tom_440_44k_mono.ogg"), files).use(::decodeAll)
        val left = samples.filterIndexed { i, _ -> i % 2 == 0 }
        val crossings = left.zipWithNext().count { (a, b) -> a < 0f && b >= 0f }
        val hertz = crossings * 48_000.0 / left.size

        assertEquals(440.0, hertz, 5.0)
    }

    @Test
    fun anEntryInsideASngDecodesAndLeavesNoTempFile() {
        val opus = File(fixture("tom_440_48k_estereo.opus")).readBytes()
        val sng = File(temp.root, "Pacote.sng").apply {
            writeBytes(SngWriter.write(mapOf("name" to "Pacote"), mapOf("guitar.opus" to opus)))
        }
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        val before = tempDir.list().orEmpty().filter { it.startsWith("riff-") }.toSet()

        val frames = FfmpegPcmSource.open(
            SngRef.entryUri(sng.path, "guitar.opus"),
            files,
        ).use { decodeAll(it).size / 2 }

        assertTrue("$frames quadros", frames in 47_000..49_500)
        val after = tempDir.list().orEmpty().filter { it.startsWith("riff-") }.toSet()
        assertEquals(before, after)
    }

    @Test
    fun seekingStartsFromThatFrame() {
        val remaining = FfmpegPcmSource.open(fixture("tom_440_48k_estereo.wav"), files).use { source ->
            source.seekTo(24_000)
            decodeAll(source).size / 2
        }

        assertEquals(24_000.0, remaining.toDouble(), 240.0)
    }

    @Test
    fun aCorruptOrUnknownFileIsAnIOException() {
        assertThrows(
            IOException::class.java,
        ) { FfmpegPcmSource.open(fixture("corrompido.ogg"), files).use(::decodeAll) }
        val unknown = File(temp.root, "x.xyz").apply { writeText("nada de áudio aqui") }
        assertThrows(IOException::class.java) { FfmpegPcmSource.open(unknown.path, files) }
        assertFalse(File(temp.root, "inexistente.ogg").exists())
        assertThrows(IOException::class.java) { FfmpegPcmSource.open(File(temp.root, "inexistente.ogg").path, files) }
    }

    private companion object {
        const val BLOCK = 1_024
        val TONES = listOf(
            "tom_440_44k_mono.ogg",
            "tom_440_48k_estereo.opus",
            "tom_440_44k_estereo.mp3",
            "tom_440_48k_estereo.wav",
        )
    }
}
