package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ChartFileParserTest {
    // Resolução 192. 120 BPM até o tick 768 (2000 ms) e 60 BPM depois (1000 ms por semínima).
    private val fullChart = """
        [Song]
        {
          Name = "Minha Música"
          Artist = "Banda"
          Album = "Disco"
          Charter = "Fulano"
          Offset = 0.25
          Resolution = 192
        }
        [SyncTrack]
        {
          0 = TS 4
          0 = B 120000
          768 = B 60000
        }
        [ExpertSingle]
        {
          192 = N 0 0
          384 = N 1 192
          768 = N 2 0
          768 = N 3 0
          960 = N 4 0
          1008 = N 3 0
          1008 = N 5 0
          1200 = N 7 0
          1392 = N 0 0
          1392 = N 6 0
        }
        [EasySingle]
        {
          192 = N 0 0
        }
        [HardSingle]
        {
        }
    """.trimIndent()

    @Test
    fun readsSongMetadataAndOffset() {
        val info = ChartFileParser.parse(fullChart).info
        assertEquals(SongInfo("Minha Música", "Banda", "Disco", "Fulano", null, 250), info)
    }

    @Test
    fun convertsExpertNotesWithTempoChangeSustainsChordsAndFlags() {
        val expert = ChartFileParser.parse(fullChart).tracks.getValue(Difficulty.EXPERT)
        assertEquals(
            listOf(
                Note(500, 0, 0, false),
                Note(1000, 1, 500, false),
                Note(2000, 2, 0, false),
                Note(2000, 3, 0, false),
                Note(3000, 4, 0, false),
                Note(3250, 3, 0, false), // HOPO natural invertido pela flag N 5
                Note(5250, 0, 0, true), // tap (N 6); a nota aberta (N 7) do tick 1200 é ignorada
            ),
            expert,
        )
    }

    @Test
    fun omitsMissingAndEmptyDifficulties() {
        assertEquals(setOf(Difficulty.EASY, Difficulty.EXPERT), ChartFileParser.parse(fullChart).tracks.keys)
    }

    @Test
    fun detectsNaturalHopo() {
        val chart = "[ExpertSingle]\n{\n  0 = N 0 0\n  48 = N 1 0\n  96 = N 1 0\n}"
        val notes = ChartFileParser.parse(chart).tracks.getValue(Difficulty.EXPERT)
        assertEquals(listOf(false, true, false), notes.map { it.isHopo })
    }

    @Test
    fun assumes120BpmAndResolution192WithoutSongOrSyncTrack() {
        val chart = "[ExpertSingle]\n{\n  192 = N 0 0\n}"
        val parsed = ChartFileParser.parse(chart)
        assertEquals(500L, parsed.tracks.getValue(Difficulty.EXPERT).single().timeMs)
        assertNull(parsed.info.title)
    }

    @Test
    fun handlesUtf8BomAndCrlfLineEndings() {
        val chart = "﻿[Song]\r\n{\r\n  Resolution = 480\r\n}\r\n[ExpertSingle]\r\n{\r\n  480 = N 2 0\r\n}\r\n"
        assertEquals(listOf(Note(500, 2, 0, false)), ChartFileParser.parse(chart).tracks.getValue(Difficulty.EXPERT))
    }

    @Test
    fun reportsLineOfInvalidNote() {
        val error = assertThrows<ChartParseException> { ChartFileParser.parse("[ExpertSingle]\n{\n  abc = N 0 0\n}") }
        assertEquals(3, error.line)
        assertTrue(error.message!!.startsWith("linha 3:"))
    }

    @Test
    fun rejectsZeroTempo() {
        val chart = "[SyncTrack]\n{\n  0 = B 0\n}\n[ExpertSingle]\n{\n  0 = N 0 0\n}"
        assertEquals(3, assertThrows<ChartParseException> { ChartFileParser.parse(chart) }.line)
    }

    @Test
    fun treatsBlankSongValuesAsMissing() {
        val chart = "[Song]\n{\n  Name = \"\"\n  Artist = \"  \"\n  Album = \n}\n[ExpertSingle]\n{\n  0 = N 0 0\n}"
        val info = ChartFileParser.parse(chart).info
        assertNull(info.title)
        assertNull(info.artist)
        assertNull(info.album)
    }

    @Test
    fun emptyTextFieldsInTheSongSectionCountAsMissing() {
        val chart = fullChart
            .replace("Name = \"Minha Música\"", "Name = \"\"")
            .replace("Artist = \"Banda\"", "Artist = \"  \"")

        val info = ChartFileParser.parse(chart).info

        assertEquals(null, info.title)
        assertEquals(null, info.artist)
    }

    @Test
    fun anOpenNoteCountsAsThePreviousNoteForTheNaturalHopo() {
        // Resolução 192: HOPO natural até 65 ticks. Verde, aberta 48 ticks depois, verde 48 ticks depois.
        val chart = """
            [Song]
            {
              Resolution = 192
            }
            [SyncTrack]
            {
              0 = B 120000
            }
            [ExpertSingle]
            {
              0 = N 0 0
              48 = N 7 0
              96 = N 0 0
            }
        """.trimIndent()

        val notes = ChartFileParser.parse(chart).tracks.getValue(Difficulty.EXPERT)

        assertEquals(2, notes.size) // a aberta não é jogada
        assertEquals(true, notes[1].isHopo) // como no Clone Hero: verde logo depois da aberta é HOPO
    }
}
