package com.rodrigomachado.riff.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NoteTest {
    @Test
    fun acceptsAllFiveLanes() {
        val lanes = (0 until Note.LANE_COUNT).map { Note(timeMs = 0, lane = it, sustainMs = 0, isHopo = false).lane }
        assertEquals(listOf(0, 1, 2, 3, 4), lanes)
    }

    @Test
    fun rejectsLaneOutsideRange() {
        assertThrows<IllegalArgumentException> { Note(timeMs = 0, lane = 5, sustainMs = 0, isHopo = false) }
        assertThrows<IllegalArgumentException> { Note(timeMs = 0, lane = -1, sustainMs = 0, isHopo = false) }
    }

    @Test
    fun rejectsNegativeSustain() {
        assertThrows<IllegalArgumentException> { Note(timeMs = 0, lane = 0, sustainMs = -1, isHopo = false) }
    }
}
