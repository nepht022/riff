package com.rodrigomachado.riff.core.game

import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.model.Note
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A palheta (PC): segurar as cores e palhetar; HOPO e tap sem palhetar, como no Clone Hero. */
class GameEngineStrumTest {
    private fun note(timeMs: Long, lane: Int, sustainMs: Long = 0, hopo: Boolean = false, tap: Boolean = false) =
        Note(timeMs, lane, sustainMs, isHopo = hopo, isTap = tap)

    @Test
    fun aStrumWithTheRightFretHitsTheNote() {
        val engine = GameEngine(listOf(note(1_000, 0)))

        assertEquals(listOf(Judgment.PERFECT), engine.onStrum(setOf(0), 1_010))
        assertEquals(1, engine.combo)
    }

    @Test
    fun anchoringLowerFretsIsAllowedOnSingleNotes() {
        val engine = GameEngine(listOf(note(1_000, 2)))

        assertEquals(listOf(Judgment.PERFECT), engine.onStrum(setOf(0, 2), 1_000))
    }

    @Test
    fun aHigherFretHeldMissesTheSingleNote() {
        val engine = GameEngine(listOf(note(1_000, 1)))

        assertNull(engine.onStrum(setOf(1, 3), 1_000))
        assertEquals(0, engine.combo)
        assertNull(engine.judgmentOf(0)) // ainda pode ser acertada dentro da janela
        assertEquals(listOf(Judgment.GOOD), engine.onStrum(setOf(1), 1_080))
    }

    @Test
    fun aChordNeedsExactlyItsFrets() {
        val engine = GameEngine(listOf(note(1_000, 0), note(1_000, 2)))

        assertNull(engine.onStrum(setOf(0), 1_000))
        assertNull(engine.onStrum(setOf(0, 1, 2), 1_000))
        assertEquals(listOf(Judgment.PERFECT, Judgment.PERFECT), engine.onStrum(setOf(0, 2), 1_000))
    }

    @Test
    fun aChordCountsAsItsNotes() {
        val engine = GameEngine(listOf(note(1_000, 0), note(1_000, 1), note(1_000, 4)))

        engine.onStrum(setOf(0, 1, 4), 1_000)

        assertEquals(3, engine.combo)
        assertEquals(300L, engine.score)
        assertEquals(1f, engine.accuracy())
    }

    @Test
    fun strummingWithNothingToHitBreaksTheCombo() {
        val engine = GameEngine((0 until 5).map { note(1_000L + it * 500, 0) } + note(9_000, 0))
        (0 until 5).forEach { engine.onStrum(setOf(0), 1_000L + it * 500) }

        assertNull(engine.onStrum(setOf(0), 5_000))
        assertEquals(0, engine.combo)
    }

    @Test
    fun aHopoAfterAHitNeedsNoStrum() {
        val engine = GameEngine(listOf(note(1_000, 0), note(1_200, 1, hopo = true)))
        engine.onStrum(setOf(0), 1_000)

        assertEquals(listOf(Judgment.PERFECT), engine.onFret(1, setOf(1), 1_200))
    }

    @Test
    fun aHopoAfterAMissNeedsAStrum() {
        val engine = GameEngine(listOf(note(1_000, 0), note(1_500, 1, hopo = true)))
        engine.update(1_300) // a primeira passou sem ser tocada

        assertNull(engine.onFret(1, setOf(1), 1_500))
        assertEquals(listOf(Judgment.PERFECT), engine.onStrum(setOf(1), 1_500))
    }

    @Test
    fun aTapNeedsNoStrumEvenAfterAMiss() {
        val engine = GameEngine(listOf(note(1_000, 0), note(1_500, 3, tap = true)))
        engine.update(1_300)

        assertEquals(listOf(Judgment.PERFECT), engine.onFret(3, setOf(3), 1_500))
    }

    @Test
    fun aStrumRightAfterAFretHitIsNotAnOverstrum() {
        val engine = GameEngine(listOf(note(1_000, 0), note(1_200, 1, hopo = true), note(3_000, 0)))
        engine.onStrum(setOf(0), 1_000)
        engine.onFret(1, setOf(1), 1_200)

        assertEquals(emptyList<Judgment>(), engine.onStrum(setOf(1), 1_230))
        assertEquals(2, engine.combo)
    }

    @Test
    fun withoutMarksEveryNoteNeedsAStrum() {
        val engine = GameEngine(listOf(note(1_000, 0), note(1_200, 1)))
        engine.onStrum(setOf(0), 1_000)

        assertNull(engine.onFret(1, setOf(1), 1_200))
        assertEquals(listOf(Judgment.PERFECT), engine.onStrum(setOf(1), 1_200))
    }

    @Test
    fun releasingTheFretEndsItsSustain() {
        val engine = GameEngine(listOf(note(1_000, 2, sustainMs = 1_000)))
        engine.onStrum(setOf(2), 1_000)
        engine.update(1_500)
        val atRelease = engine.score

        engine.onRelease(2, 1_500)
        engine.update(2_000)

        assertTrue(atRelease > 100)
        assertEquals(atRelease, engine.score)
    }
}
