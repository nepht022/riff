package com.rodrigomachado.riff.core.model

/** Uma nota já convertida para milissegundos. Um acorde é representado por várias notas com o mesmo [timeMs]. */
data class Note(val timeMs: Long, val lane: Int, val sustainMs: Long, val isHopo: Boolean) {
    init {
        require(lane in 0 until LANE_COUNT) { "lane fora de 0..4: $lane" }
        require(sustainMs >= 0) { "sustainMs negativo: $sustainMs" }
    }

    companion object {
        const val LANE_COUNT = 5
    }
}

/** Notas de uma dificuldade, em ordem de tempo. */
data class ChartTrack(val chartId: String, val difficulty: Difficulty, val notes: List<Note>)
