package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.MessageDigest

class ChartLoaderTest {
    private val chartText = """
        [Song]
        {
          Name = "Do Chart"
          Artist = "Banda do Chart"
          Offset = 0.1
        }
        [ExpertSingle]
        {
          192 = N 0 0
        }
    """.trimIndent()
    private val midiBytes = MidiBuilder().track { tempo(0, 500_000) }.track("PART GUITAR") { note(480, 96, 60) }.build()

    private fun folder(vararg files: Pair<String, ByteArray>) = ChartFiles { name ->
        files.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
    }

    @Test
    fun loadsChartWithItsOwnMetadataAndOffset() {
        val loaded = ChartLoader.load(folder("notes.chart" to chartText.toByteArray()))
        assertEquals(ChartFormat.CHART, loaded.format)
        assertEquals("Do Chart", loaded.info.title)
        assertEquals(100L, loaded.info.offsetMs)
        assertEquals(600L, loaded.tracks.getValue(Difficulty.EXPERT).single().timeMs)
    }

    @Test
    fun songIniOverridesMetadataAndAddsDelay() {
        val ini = "[Song]\nName = Do Ini\nDelay = 50"
        val loaded = ChartLoader.load(folder("NOTES.CHART" to chartText.toByteArray(), "Song.ini" to ini.toByteArray()))
        assertEquals("Do Ini", loaded.info.title)
        assertEquals("Banda do Chart", loaded.info.artist)
        assertEquals(150L, loaded.info.offsetMs)
        assertEquals(650L, loaded.tracks.getValue(Difficulty.EXPERT).single().timeMs)
    }

    @Test
    fun usesMidiWhenThereIsNoChart() {
        val ini = "[song]\nname = Do Ini\nartist = Banda"
        val loaded = ChartLoader.load(folder("notes.mid" to midiBytes, "song.ini" to ini.toByteArray()))
        assertEquals(ChartFormat.MIDI, loaded.format)
        assertEquals("Do Ini", loaded.info.title)
        assertEquals(500L, loaded.tracks.getValue(Difficulty.EXPERT).single().timeMs)
    }

    @Test
    fun prefersChartOverMidi() {
        val loaded = ChartLoader.load(folder("notes.mid" to midiBytes, "notes.chart" to chartText.toByteArray()))
        assertEquals(ChartFormat.CHART, loaded.format)
    }

    @Test
    fun midiWithoutSongIniHasNoMetadata() {
        val loaded = ChartLoader.load(folder("notes.mid" to midiBytes))
        assertNull(loaded.info.title)
    }

    @Test
    fun idIsSha1OfNotesFile() {
        val bytes = chartText.toByteArray()
        val expected = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(expected, ChartLoader.load(folder("notes.chart" to bytes)).id)
    }

    @Test
    fun failsWithoutNotesFile() {
        assertThrows<ChartParseException> { ChartLoader.load(folder("song.ini" to "[song]".toByteArray())) }
    }

    @Test
    fun failsWhenThereIsNoGuitarDifficulty() {
        val noNotes = "[Song]\n{\n  Name = \"Vazio\"\n}"
        assertThrows<ChartParseException> { ChartLoader.load(folder("notes.chart" to noNotes.toByteArray())) }
    }

    @Test
    fun readsUtf16ChartWithBom() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + chartText.toByteArray(Charsets.UTF_16LE)
        val loaded = ChartLoader.load(folder("notes.chart" to bytes))
        assertEquals("Do Chart", loaded.info.title)
        assertEquals(600L, loaded.tracks.getValue(Difficulty.EXPERT).single().timeMs)
    }

    @Test
    fun readsLatin1SongIni() {
        val ini = "[song]\nartist = Motörhead".toByteArray(Charsets.ISO_8859_1)
        val loaded = ChartLoader.load(folder("notes.chart" to chartText.toByteArray(), "song.ini" to ini))
        assertEquals("Motörhead", loaded.info.artist)
    }

    @Test
    fun negativeTickBecomesParseError() {
        val chart = "[ExpertSingle]\n{\n  -5 = N 0 0\n}"
        assertThrows<ChartParseException> { ChartLoader.load(folder("notes.chart" to chart.toByteArray())) }
    }

    @Test
    fun zeroResolutionBecomesParseError() {
        val chart = "[Song]\n{\n  Resolution = 0\n}\n[ExpertSingle]\n{\n  0 = N 0 0\n}"
        assertThrows<ChartParseException> { ChartLoader.load(folder("notes.chart" to chart.toByteArray())) }
    }

    @Test
    fun corruptMidiChunkLengthBecomesParseError() {
        val bytes = midiBytes.copyOf()
        val trackLength = 14 + 4 // cabeçalho MThd (14 bytes) + "MTrk"
        bytes[trackLength] = 0x80.toByte() // tamanho com o bit mais alto ligado: vira negativo
        assertThrows<ChartParseException> { ChartLoader.load(folder("notes.mid" to bytes)) }
    }
}
