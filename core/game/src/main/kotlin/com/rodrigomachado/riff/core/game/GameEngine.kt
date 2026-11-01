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
@Suppress("TooManyFunctions") // julgamento, contagem e notas longas dividem o mesmo estado da partida
class GameEngine(notes: List<Note>, private val windows: JudgmentWindows = JudgmentWindows()) {
    val notes: List<Note> = notes.sortedWith(compareBy<Note> { it.timeMs }.thenBy { it.lane })

    private val judgments = arrayOfNulls<Judgment>(this.notes.size)
    private val counts = IntArray(Judgment.entries.size)
    private val sustains = mutableMapOf<Int, ActiveSustain>()
    private var firstPending = 0
    private var hitScore = 0L
    private var sustainScore = 0.0

    /** Acertos seguidos; leitura sem montar o [state] (o placar lê a cada quadro). */
    var combo = 0
        private set
    private var maxCombo = 0

    /** "Tempo sem errar": o maior trecho sem erro, do 1º ao último acerto dele, no tempo do mapa. */
    var longestCleanMs = 0L
        private set
    private var streakStartMs = 0L
    private var songTimeMs = 0L

    // Nota do último acerto sem palheta (HOPO/tap): a palhetada na janela dela é perdoada.
    private var lastFretHitNoteMs = Long.MIN_VALUE / 2

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

    /** Quantas notas já receberam [judgment]. Não aloca: pode ser chamado a cada frame. */
    fun count(judgment: Judgment): Int = counts[judgment.ordinal]

    fun accuracy(): Float = if (notes.isEmpty()) {
        0f
    } else {
        (counts[Judgment.PERFECT.ordinal] + HALF * counts[Judgment.GOOD.ordinal]) / notes.size
    }

    /**
     * Avança a partida até [timeMs]. [missGraceMs] atrasa só a marcação de erros: um toque feito no fim da janela,
     * mas processado depois deste quadro, ainda pode acertar a nota.
     */
    fun update(timeMs: Long, missGraceMs: Long = 0) {
        songTimeMs = timeMs
        markMisses(timeMs - missGraceMs)
        accrueSustains(timeMs)
    }

    fun onPress(lane: Int, timeMs: Long): Judgment? {
        update(timeMs)
        val index = findHittable(lane, timeMs)
        if (index == null) {
            combo = 0 // toque sem nota quebra o combo e o "tempo sem errar"
            return null
        }
        return hit(index, timeMs)
    }

    /**
     * Palhetada (PC) com as cores [held] seguradas: acerta o primeiro acorde pendente da janela que bate com as
     * cores (nota simples aceita cores mais baixas seguradas junto; acorde exige exatamente as cores dele), e os
     * pendentes antes dele viram erro: perder uma nota não trava as seguintes. Cada nota do acorde conta como um
     * acerto. Palhetada sem nota ou com cor errada quebra o combo (`null`), como no Clone Hero; dentro da janela de
     * uma nota que a cor já acertou sem palheta (HOPO/tap), ela é perdoada (lista vazia).
     */
    fun onStrum(held: Set<Int>, timeMs: Long): List<Judgment>? {
        update(timeMs)
        val chord = matchingChord(held, timeMs)
        if (chord != null) {
            missPendingBefore(notes[chord.first()].timeMs)
            return chord.map { hit(it, timeMs) }
        }
        if (abs(timeMs - lastFretHitNoteMs) <= windows.goodMs) return emptyList()
        combo = 0
        return null
    }

    /**
     * As cores seguradas mudaram (apertou ou soltou uma, com a palheta ligada, no PC): acerta sem palhetar o tap,
     * sempre, e o HOPO, só se a nota anterior foi acertada — inclusive soltando a cor de cima (pull-off). Fora disso
     * não faz nada (`null`, sem quebrar o combo).
     */
    fun onFretsChanged(held: Set<Int>, timeMs: Long): List<Judgment>? {
        update(timeMs)
        val chord = nextChord(timeMs) ?: return null
        val first = notes[chord.first()]
        val withoutStrum = first.isTap || (first.isHopo && combo > 0)
        if (!withoutStrum || !fretsMatch(chord, held)) return null
        lastFretHitNoteMs = first.timeMs
        return chord.map { hit(it, timeMs) }
    }

    /** O primeiro acorde pendente da janela cujas cores batem com [held]. */
    private fun matchingChord(held: Set<Int>, timeMs: Long): List<Int>? {
        var i = firstPending
        while (i < notes.size && notes[i].timeMs <= timeMs + windows.goodMs) {
            val chordTime = notes[i].timeMs
            val chord = (i until notes.size).takeWhile { notes[it].timeMs == chordTime }
            val pending = chord.filter { judgments[it] == null }
            if (chordTime >= timeMs - windows.goodMs && pending.isNotEmpty() && fretsMatch(pending, held)) {
                return pending
            }
            i += chord.size
        }
        return null
    }

    /** As notas pendentes antes de [timeMs] viram erro (o jogador pulou para a seguinte). */
    private fun missPendingBefore(timeMs: Long) {
        var i = firstPending
        while (i < notes.size && notes[i].timeMs < timeMs) {
            if (judgments[i] == null) {
                record(i, Judgment.MISS)
                combo = 0
            }
            i++
        }
    }

    /** O acorde (índices) mais cedo ainda sem julgamento dentro da janela de acerto. */
    private fun nextChord(timeMs: Long): List<Int>? {
        var i = firstPending
        while (i < notes.size && notes[i].timeMs <= timeMs + windows.goodMs) {
            if (judgments[i] == null && notes[i].timeMs >= timeMs - windows.goodMs) {
                val chordTime = notes[i].timeMs
                return (i until notes.size).takeWhile { notes[it].timeMs == chordTime }.filter { judgments[it] == null }
            }
            i++
        }
        return null
    }

    private fun fretsMatch(chord: List<Int>, held: Set<Int>): Boolean {
        val lanes = chord.map { notes[it].lane }.toSet()
        return if (lanes.size == 1) held.maxOrNull() == lanes.single() else held == lanes
    }

    /** Registra o acerto da nota [index] tocada em [timeMs]: julgamento, pontos, combo e sustain. */
    private fun hit(index: Int, timeMs: Long): Judgment {
        val note = notes[index]
        val judgment = if (abs(note.timeMs - timeMs) <= windows.perfectMs) Judgment.PERFECT else Judgment.GOOD
        record(index, judgment)
        // Como no Guitar Hero: o acerto vale o multiplicador de antes dele; o x2 começa no 11º acerto seguido.
        hitScore += (if (judgment == Judgment.PERFECT) PERFECT_POINTS else GOOD_POINTS) * multiplier
        if (combo == 0) streakStartMs = note.timeMs
        combo++
        maxCombo = max(maxCombo, combo)
        longestCleanMs = max(longestCleanMs, note.timeMs - streakStartMs)
        if (note.sustainMs > 0) {
            sustains[note.lane] = ActiveSustain(note.timeMs + note.sustainMs, max(timeMs, note.timeMs))
        }
        return judgment
    }

    fun onRelease(lane: Int, timeMs: Long) {
        update(timeMs)
        sustains.remove(lane)
    }

    /** Fim da partida antes do mapa (o áudio acabou): as notas que nunca chegaram contam como erro. */
    fun finish() {
        markMisses(Long.MAX_VALUE)
        sustains.clear()
    }

    /**
     * Há uma nota que já passou do acerto perfeito e ainda não foi tocada (mas ainda pode ser, atrasada).
     * Com a guitarra separada, ela silencia aqui em vez de esperar o fim da janela.
     */
    fun hasOverdueNote(timeMs: Long): Boolean {
        var i = firstPending
        while (i < notes.size && notes[i].timeMs < timeMs - windows.perfectMs) {
            if (judgments[i] == null) return true
            i++
        }
        return false
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
