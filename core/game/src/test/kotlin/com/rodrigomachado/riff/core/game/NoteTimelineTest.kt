package com.rodrigomachado.riff.core.game

import com.rodrigomachado.riff.core.model.Note
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteTimelineTest {
    private val notes = listOf(100L, 200L, 200L, 300L, 500L).mapIndexed { i, t -> Note(t, i % 5, 0, false) }

    @Test
    fun firstAtOrAfterFindsTheLowerBoundIncludingChords() {
        assertEquals(0, NoteTimeline.firstAtOrAfter(notes, 0))
        assertEquals(1, NoteTimeline.firstAtOrAfter(notes, 200))
        assertEquals(3, NoteTimeline.firstAtOrAfter(notes, 201))
        assertEquals(5, NoteTimeline.firstAtOrAfter(notes, 600))
    }

    @Test
    fun visibleRangeIsInclusiveOnBothEnds() {
        assertEquals(1..3, NoteTimeline.visibleRange(notes, 200, 300))
        assertEquals(0..4, NoteTimeline.visibleRange(notes, 0, 1_000))
        assertEquals(true, NoteTimeline.visibleRange(notes, 600, 900).isEmpty())
        assertEquals(true, NoteTimeline.visibleRange(emptyList(), 0, 100).isEmpty())
    }
}
