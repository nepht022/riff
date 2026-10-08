package com.rodrigomachado.riff.core.game

import com.rodrigomachado.riff.core.model.GameState
import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.model.Note
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class JudgmentWindows(val perfectMs: Long = 50, val goodMs: Long = 100) {
    init {
        require(perfectMs in 1..goodMs) { "janelas inválidas: perfeito=$perfectMs, bom=$goodMs" }
    }
}

/**
 * Regras da partida: julga toques, conta combo e multiplicador, pontua notas longas.
 * Não conhece relógio nem UI: recebe sempre o tempo da música em ms.
 */
class GameEngine(notes: List<Note>, private val windows: JudgmentWindows = JudgmentWindows()) {
    val notes: List<Note> = notes.sortedWith(compareBy<Note> { it.timeMs }.thenBy { it.lane })

    private val judgments = arrayOfNulls<Judgment>(this.notes.size)
    private val counts = IntArray(Judgment.entries.size)
    private val sustains = mutableMapOf<Int, ActiveSustain>()
    private var firstPending = 0
    private var hitScore = 0L
    private var sustainScore = 0.0
    private var combo = 0
    private var maxCombo = 0
    private var songTimeMs = 0L

    private class ActiveSustain(val endMs: Long, var scoredUntilMs: Long)

    val multiplier: Int get() = min(MAX_MULTIPLIER, 1 + combo / HITS_PER_MULTIPLIER_STEP)

    val score: Long get() = hitScore + sustainScore.toLong()

    val isFinished: Boolean get() = counts.sum() == notes.size && sustains.isEmpty()

    val state: GameState
        get() = GameState(
            songTimeMs = songTimeMs,
            score = score,
            combo = combo,
            maxCombo = maxCombo,
            multiplier = multiplier,
            counts = Judgment.entries.associateWith { counts[it.ordinal] },
            activeSustains = sustains.keys.toSet(),
        )

    fun judgmentOf(index: Int): Judgment? = judgments[index]

    fun accuracy(): Float = if (notes.isEmpty()) {
        0f
    } else {
        (counts[Judgment.PERFECT.ordinal] + HALF * counts[Judgment.GOOD.ordinal]) / notes.size
    }

    fun update(timeMs: Long) {
        songTimeMs = timeMs
        markMisses(timeMs)
        accrueSustains(timeMs)
    }

    fun onPress(lane: Int, timeMs: Long): Judgment? {
        update(timeMs)
        val index = findHittable(lane, timeMs)
        if (index == null) {
            combo = 0
            return null
        }
        val note = notes[index]
        val judgment = if (abs(note.timeMs - timeMs) <= windows.perfectMs) Judgment.PERFECT else Judgment.GOOD
        record(index, judgment)
        // Como no Guitar Hero: o acerto vale o multiplicador de antes dele; o x2 começa no 11º acerto seguido.
        hitScore += (if (judgment == Judgment.PERFECT) PERFECT_POINTS else GOOD_POINTS) * multiplier
        combo++
        maxCombo = max(maxCombo, combo)
        if (note.sustainMs > 0) {
            sustains[lane] = ActiveSustain(note.timeMs + note.sustainMs, max(timeMs, note.timeMs))
        }
        return judgment
    }

    fun onRelease(lane: Int, timeMs: Long) {
        update(timeMs)
        sustains.remove(lane)
    }

    private fun findHittable(lane: Int, timeMs: Long): Int? {
        var i = firstPending
        while (i < notes.size && notes[i].timeMs <= timeMs + windows.goodMs) {
            val note = notes[i]
            if (judgments[i] == null && note.lane == lane && note.timeMs >= timeMs - windows.goodMs) return i
            i++
        }
        return null
    }

    private fun markMisses(timeMs: Long) {
        while (firstPending < notes.size && notes[firstPending].timeMs < timeMs - windows.goodMs) {
            if (judgments[firstPending] == null) {
                record(firstPending, Judgment.MISS)
                combo = 0
            }
            firstPending++
        }
    }

    private fun accrueSustains(timeMs: Long) {
        val iterator = sustains.values.iterator()
        while (iterator.hasNext()) {
            val sustain = iterator.next()
            val until = min(timeMs, sustain.endMs)
            if (until > sustain.scoredUntilMs) {
                sustainScore += (until - sustain.scoredUntilMs) * SUSTAIN_POINTS_PER_MS * multiplier
                sustain.scoredUntilMs = until
            }
            if (timeMs >= sustain.endMs) iterator.remove()
        }
    }

    private fun record(index: Int, judgment: Judgment) {
        judgments[index] = judgment
        counts[judgment.ordinal]++
    }

    private companion object {
        const val PERFECT_POINTS = 100L
        const val GOOD_POINTS = 50L
        const val SUSTAIN_POINTS_PER_MS = 25.0 / 500.0
        const val MAX_MULTIPLIER = 4
        const val HITS_PER_MULTIPLIER_STEP = 10
        const val HALF = 0.5f
    }
}
