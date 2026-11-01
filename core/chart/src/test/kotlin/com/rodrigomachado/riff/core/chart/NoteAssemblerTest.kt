package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.model.Note
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteAssemblerTest {
    private val tempo = TempoMap(192, emptyList()) // 120 BPM: 192 ticks = 500 ms
    private val threshold = 65L

    private fun assemble(vararg chords: RawChord) = NoteAssembler.assemble(chords.toList(), tempo, threshold)

    private fun single(tick: Long, lane: Int, length: Long = 0, forcing: Forcing = Forcing.NONE, tap: Boolean = false) =
        RawChord(tick, mapOf(lane to length), forcing, tap)

    @Test
    fun farApartNotesAreStrums() {
        val notes = assemble(single(0, 0), single(192, 1))
        assertEquals(listOf(Note(0, 0, 0, false), Note(500, 1, 0, false)), notes)
    }

    @Test
    fun closeNoteOnDifferentLaneIsNaturalHopo() {
        val notes = assemble(single(0, 0), single(48, 1))
        assertEquals(true, notes[1].isHopo)
    }

    @Test
    fun closeNoteOnSameLaneIsNotHopo() {
        val notes = assemble(single(0, 1), single(48, 1))
        assertEquals(false, notes[1].isHopo)
    }

    @Test
    fun chordIsNeverNaturalHopo() {
        val notes = assemble(single(0, 0), RawChord(48, mapOf(1 to 0L, 2 to 0L)))
        assertEquals(listOf(false, false), notes.drop(1).map { it.isHopo })
    }

    @Test
    fun flipForcingInvertsNaturalHopo() {
        val notes = assemble(
            single(0, 0),
            single(48, 1, forcing = Forcing.FLIP),
            single(240, 2, forcing = Forcing.FLIP),
        )
        assertEquals(listOf(false, false, true), notes.map { it.isHopo })
    }

    @Test
    fun explicitForcingWins() {
        val notes = assemble(
            single(0, 0),
            single(48, 1, forcing = Forcing.STRUM),
            single(240, 2, forcing = Forcing.HOPO),
        )
        assertEquals(listOf(false, false, true), notes.map { it.isHopo })
    }

    /** Tap é tap, não HOPO: com a palheta, vale sem palhetar mesmo depois de um erro (o HOPO não). */
    @Test
    fun tapIsMarkedAsTapNotHopo() {
        val note = assemble(single(0, 3, tap = true)).single()

        assertEquals(true, note.isTap)
        assertEquals(false, note.isHopo)
    }

    @Test
    fun naturalAndForcedNotesAreNotTaps() {
        val notes = assemble(single(0, 0), single(60, 1), single(120, 2, forcing = Forcing.STRUM))

        assertEquals(listOf(false, true, false), notes.map { it.isHopo })
        assertEquals(listOf(false, false, false), notes.map { it.isTap })
    }

    @Test
    fun convertsSustainLengthToMs() {
        assertEquals(500L, assemble(single(0, 0, length = 192)).single().sustainMs)
    }

    @Test
    fun chordProducesOneNotePerLaneInLaneOrder() {
        val notes = assemble(RawChord(192, mapOf(3 to 0L, 1 to 0L)))
        assertEquals(listOf(Note(500, 1, 0, false), Note(500, 3, 0, false)), notes)
    }

    @Test
    fun skipsChordsWithoutLanes() {
        val notes = assemble(single(0, 0), RawChord(24, emptyMap()), single(48, 1))
        assertEquals(listOf(0L, 125L), notes.map { it.timeMs })
        assertEquals(true, notes[1].isHopo)
    }
}
