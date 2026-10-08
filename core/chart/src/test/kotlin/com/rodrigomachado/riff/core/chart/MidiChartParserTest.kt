package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MidiChartParserTest {
    // 480 ticks por semínima, 120 BPM: 480 ticks = 500 ms.
    private val defaultTempo: MidiBuilder.TrackBuilder.() -> Unit = { tempo(0, 500_000) }

    private fun midi(
        tempos: MidiBuilder.TrackBuilder.() -> Unit = defaultTempo,
        guitar: MidiBuilder.TrackBuilder.() -> Unit,
    ) = MidiBuilder().track(block = tempos).track("PART GUITAR", guitar).build()

    private fun expert(bytes: ByteArray) = MidiChartParser.parse(bytes).tracks.getValue(Difficulty.EXPERT)

    @Test
    fun convertsExpertNotesAndCutsShortSustains() {
        val notes = expert(
            midi {
                note(960, 96, 480)
                note(1440, 97, 60)
            },
        )
        assertEquals(listOf(Note(1000, 0, 500, false), Note(1500, 1, 0, false)), notes)
    }

    @Test
    fun mapsEachDifficultyFromItsKeyRange() {
        val bytes = midi {
            note(0, 60, 60)
            note(0, 76, 60)
            note(0, 88, 60)
        }
        val tracks = MidiChartParser.parse(bytes).tracks
        assertEquals(Note(0, 0, 0, false), tracks.getValue(Difficulty.EASY).single())
        assertEquals(Note(0, 4, 0, false), tracks.getValue(Difficulty.MEDIUM).single())
        assertEquals(Note(0, 4, 0, false), tracks.getValue(Difficulty.HARD).single())
        assertEquals(setOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD), tracks.keys)
    }

    @Test
    fun forceHopoMarkerCoversNotesInsideItsSpan() {
        val notes = expert(
            midi {
                note(0, 96, 60)
                note(1900, 101, 100)
                note(1920, 98, 60)
            },
        )
        assertEquals(listOf(false, true), notes.map { it.isHopo })
    }

    @Test
    fun forceStrumMarkerCancelsNaturalHopo() {
        val natural = expert(
            midi {
                note(0, 96, 60)
                note(120, 97, 60)
            },
        )
        val forced = expert(
            midi {
                note(0, 96, 60)
                note(120, 97, 60)
                note(120, 102, 10)
            },
        )
        assertEquals(true, natural[1].isHopo)
        assertEquals(false, forced[1].isHopo)
    }

    @Test
    fun appliesTempoChangesFromTempoTrack() {
        val tempos: MidiBuilder.TrackBuilder.() -> Unit = {
            tempo(0, 500_000)
            tempo(960, 1_000_000)
        }
        val notes = expert(midi(tempos) { note(1440, 96, 60) })
        assertEquals(2000L, notes.single().timeMs)
    }

    @Test
    fun handlesRunningStatusAndNoteOnWithZeroVelocityAsNoteOff() {
        val notes = expert(
            midi {
                raw(0, 0x90, 96, 100)
                raw(480, 96, 0)
                raw(960, 97, 100)
                raw(1440, 97, 0)
            },
        )
        assertEquals(listOf(Note(0, 0, 500, false), Note(1000, 1, 500, false)), notes)
    }

    @Test
    fun failsWithoutGuitarTrack() {
        val bytes = MidiBuilder().track { tempo(0, 500_000) }.track("PART BASS") { note(0, 96, 60) }.build()
        assertThrows<ChartParseException> { MidiChartParser.parse(bytes) }
    }

    @Test
    fun failsOnNonMidiAndTruncatedData() {
        assertThrows<ChartParseException> { MidiChartParser.parse("não é midi".toByteArray()) }
        val valid = midi { note(0, 96, 60) }
        assertThrows<ChartParseException> { MidiChartParser.parse(valid.copyOf(valid.size - 6)) }
    }

    @Test
    fun noteOffAfterRetriggerOnSameTickClosesThePreviousNote() {
        // Alguns exportadores gravam o note-on da nota seguinte antes do note-off da anterior, no mesmo tick.
        val notes = expert(
            midi {
                raw(0, 0x90, 96, 100)
                raw(480, 0x90, 96, 100)
                raw(480, 0x80, 96, 0)
                raw(960, 0x80, 96, 0)
            },
        )
        assertEquals(listOf(Note(0, 0, 500, false), Note(500, 0, 500, false)), notes)
    }
}
