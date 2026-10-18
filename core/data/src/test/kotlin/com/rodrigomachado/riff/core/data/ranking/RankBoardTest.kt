package com.rodrigomachado.riff.core.data.ranking

import com.rodrigomachado.riff.core.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Test

/** O placar montado no celular a partir das linhas do script: ordenar e filtrar não volta ao servidor. */
class RankBoardTest {
    private fun entry(
        name: String,
        points: Long,
        difficulty: Difficulty = Difficulty.HARD,
        you: Boolean = false,
        perfect: Int = 40,
        notes: Int? = 85,
        clean: Long? = 30_000,
    ) = BoardEntry(name, you, difficulty, points.toDouble(), 0.9, 50.0, perfect.toDouble(), clean?.toDouble(), notes)

    private val entries = listOf(
        entry("Ana", 300),
        entry("Bruno", 200, you = true),
        entry("Carla", 100, clean = null),
        entry("Zé", 999, difficulty = Difficulty.EASY),
    )

    @Test
    fun ordersByTheMetricInBothDirections() {
        val desc = rankBoard(entries, Difficulty.HARD, RankingMetric.POINTS, descending = true, query = "")
        assertEquals(listOf("Ana" to 1, "Bruno" to 2, "Carla" to 3), desc.lines.map { it.name to it.position })
        assertEquals(true, desc.lines[1].you)

        val asc = rankBoard(entries, Difficulty.HARD, RankingMetric.POINTS, descending = false, query = "")
        assertEquals(listOf("Carla", "Bruno", "Ana"), asc.lines.map { it.name })
    }

    @Test
    fun searchingKeepsTheRealPosition() {
        val found = rankBoard(entries, Difficulty.HARD, RankingMetric.POINTS, descending = true, query = "car")

        assertEquals(listOf(BoardLine(3, "Carla", 100.0, you = false)), found.lines)
        assertEquals(3, found.total)
    }

    @Test
    fun difficultiesComeInGameOrderAndDefaultToTheFirst() {
        val board = rankBoard(entries, difficulty = null, RankingMetric.POINTS, descending = true, query = "")

        assertEquals(listOf(Difficulty.EASY, Difficulty.HARD), board.difficulties)
        assertEquals(Difficulty.EASY, board.difficulty)
        assertEquals(listOf("Zé"), board.lines.map { it.name })
    }

    @Test
    fun playersWithoutTheNumberAreLeftOut() {
        val board = rankBoard(entries, Difficulty.HARD, RankingMetric.CLEAN, descending = true, query = "")

        assertEquals(listOf("Ana", "Bruno"), board.lines.map { it.name })
    }

    /** Perfeitos "de quantos": o total de notas da partida vai junto. */
    @Test
    fun perfectCarriesTheTotalNotes() {
        val board = rankBoard(entries, Difficulty.HARD, RankingMetric.PERFECT, descending = true, query = "ana")

        assertEquals(85, board.lines.single().outOf)
    }
}
