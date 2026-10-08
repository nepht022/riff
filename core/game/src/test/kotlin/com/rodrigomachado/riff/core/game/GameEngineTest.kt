package com.rodrigomachado.riff.core.game

import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.model.Note
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GameEngineTest {
    private fun note(timeMs: Long, lane: Int = 0, sustainMs: Long = 0) = Note(timeMs, lane, sustainMs, isHopo = false)

    @Test
    fun judgesByDistanceToNoteIncludingWindowEdges() {
        val engine = GameEngine(listOf(note(1_000), note(2_000), note(3_000), note(4_000)))
        assertEquals(Judgment.PERFECT, engine.onPress(0, 1_050))
        assertEquals(Judgment.GOOD, engine.onPress(0, 1_949))
        assertEquals(Judgment.GOOD, engine.onPress(0, 3_100))
        assertNull(engine.onPress(0, 3_899))
    }

    @Test
    fun ghostTapBreaksComboWithoutCountingAsMiss() {
        val engine = GameEngine(listOf(note(1_000), note(5_000)))
        engine.onPress(0, 1_000)
        assertNull(engine.onPress(2, 1_200))
        assertEquals(0, engine.state.combo)
        assertEquals(0, engine.state.counts.getValue(Judgment.MISS))
    }

    @Test
    fun marksMissWhenNotePassesWindow() {
        val engine = GameEngine(listOf(note(1_000), note(2_000)))
        engine.onPress(0, 1_000)
        engine.update(2_101)
        assertEquals(Judgment.MISS, engine.judgmentOf(1))
        assertEquals(0, engine.state.combo)
        assertEquals(1, engine.state.maxCombo)
    }

    @Test
    fun pressHitsOnlyTheEarliestOfCloseNotesOnSameLane() {
        val engine = GameEngine(listOf(note(1_000), note(1_060)))
        assertEquals(Judgment.PERFECT, engine.onPress(0, 1_030))
        assertEquals(Judgment.PERFECT, engine.judgmentOf(0))
        assertNull(engine.judgmentOf(1))
        assertEquals(Judgment.PERFECT, engine.onPress(0, 1_070))
        assertEquals(2, engine.state.combo)
    }

    @Test
    fun chordNotesAreJudgedIndependently() {
        val engine = GameEngine(listOf(note(1_000, lane = 1), note(1_000, lane = 3)))
        assertEquals(Judgment.PERFECT, engine.onPress(3, 1_010))
        assertNull(engine.judgmentOf(0))
        assertEquals(Judgment.GOOD, engine.onPress(1, 1_080))
    }

    @Test
    fun multiplierGrowsEveryTenHitsUpToFour() {
        val engine = GameEngine(List(40) { note(it * 1_000L) })
        // Como no Guitar Hero: os 10 primeiros acertos valem x1; o indicador já mostra x2 para o 11º.
        repeat(10) { engine.onPress(0, it * 1_000L) }
        assertEquals(2, engine.multiplier)
        assertEquals(10 * 100L, engine.score)
        engine.onPress(0, 10_000L)
        assertEquals(10 * 100L + 200L, engine.score)
        repeat(29) { engine.onPress(0, (it + 11) * 1_000L) }
        assertEquals(4, engine.multiplier)
    }

    @Test
    fun goodScoresHalfOfPerfect() {
        val engine = GameEngine(listOf(note(1_000)))
        engine.onPress(0, 1_080)
        assertEquals(50L, engine.score)
    }

    @Test
    fun heldSustainScores25PointsPerHalfSecond() {
        val engine = GameEngine(listOf(note(1_000, sustainMs = 1_000)))
        engine.onPress(0, 1_000)
        assertEquals(setOf(0), engine.state.activeSustains)
        engine.update(1_500)
        assertEquals(125L, engine.score)
        engine.update(2_300)
        assertEquals(150L, engine.score)
        assertTrue(engine.state.activeSustains.isEmpty())
    }

    @Test
    fun releasingEarlyStopsSustainScoring() {
        val engine = GameEngine(listOf(note(1_000, sustainMs = 1_000)))
        engine.onPress(0, 1_000)
        engine.onRelease(0, 1_250)
        engine.update(2_000)
        assertEquals(112L, engine.score)
    }

    @Test
    fun latePressScoresSustainOnlyFromPressTime() {
        val engine = GameEngine(listOf(note(1_000, sustainMs = 1_000)))
        engine.onPress(0, 1_080)
        engine.update(2_000)
        assertEquals(50L + 46L, engine.score)
    }

    @Test
    fun accuracyWeighsGoodAsHalf() {
        val engine = GameEngine(listOf(note(1_000), note(2_000), note(3_000), note(4_000)))
        engine.onPress(0, 1_000)
        engine.onPress(0, 2_080)
        engine.update(3_200)
        engine.onPress(0, 4_000)
        assertEquals(0.625f, engine.accuracy(), 0.0001f)
        assertEquals(mapOf(Judgment.PERFECT to 2, Judgment.GOOD to 1, Judgment.MISS to 1), engine.state.counts)
    }

    @Test
    fun finishesWhenAllNotesJudgedAndSustainsOver() {
        val engine = GameEngine(listOf(note(1_000, sustainMs = 500)))
        assertFalse(engine.isFinished)
        engine.onPress(0, 1_000)
        assertFalse(engine.isFinished)
        engine.update(1_500)
        assertTrue(engine.isFinished)
    }

    @Test
    fun timeGoingBackwardsKeepsJudgments() {
        val engine = GameEngine(listOf(note(1_000), note(2_000)))
        engine.update(3_000)
        engine.update(500)
        assertEquals(Judgment.MISS, engine.judgmentOf(0))
        assertNull(engine.onPress(0, 1_000))
    }

    @Test
    fun emptyChartIsFinishedWithZeroAccuracy() {
        val engine = GameEngine(emptyList())
        assertTrue(engine.isFinished)
        assertEquals(0f, engine.accuracy())
    }
}
