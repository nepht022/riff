package com.rodrigomachado.riff.core.data.ranking

import com.rodrigomachado.riff.core.model.Difficulty

/** O jogador deste celular no ranking: o nome e a chave que o prova (os dois nulos antes de escolher). */
data class Player(val name: String?, val key: String?)

/** Os números do ranking, com a chave do protocolo do script. Todos: maior é melhor. */
enum class RankingMetric(val wire: String) {
    POINTS("pontos"),
    ACCURACY("precisao"),
    COMBO("combo"),
    PERFECT("perfeitos"),
    CLEAN("semErrar"),
}

sealed interface NameResult {
    data object Ok : NameResult

    data object Taken : NameResult

    data object Invalid : NameResult

    data object Offline : NameResult

    /** Tentativas demais nesta hora (o script limita as reservas). */
    data object Limit : NameResult
}

/** O que aconteceu com a partida no ranking (o Resultado mostra). */
sealed interface SubmitStatus {
    data class Placed(val position: Int, val total: Int) : SubmitStatus

    data object Sending : SubmitStatus

    /** Sem internet (ou o script não respondeu): vai quando der. */
    data object Queued : SubmitStatus

    data object NoName : SubmitStatus

    data object NotBest : SubmitStatus
}

data class RankedSong(
    val chartId: String,
    val title: String,
    val artist: String,
    val players: Int,
    val yourPosition: Int?,
)

/** [outOf]: em Perfeitos, o total de notas da partida (null em partidas de antes da medida). */
data class BoardLine(val position: Int, val name: String, val value: Double, val you: Boolean, val outOf: Int? = null)

/** Uma linha do placar como o script manda: um jogador numa dificuldade, com todos os números. */
data class BoardEntry(
    val name: String,
    val you: Boolean,
    val difficulty: Difficulty,
    val points: Double?,
    val accuracy: Double?,
    val combo: Double?,
    val perfect: Double?,
    val clean: Double?,
    val notes: Int?,
) {
    fun value(metric: RankingMetric): Double? = when (metric) {
        RankingMetric.POINTS -> points
        RankingMetric.ACCURACY -> accuracy
        RankingMetric.COMBO -> combo
        RankingMetric.PERFECT -> perfect
        RankingMetric.CLEAN -> clean
    }
}

data class Board(
    val difficulties: List<Difficulty>,
    val difficulty: Difficulty?,
    val total: Int,
    val lines: List<BoardLine>,
)

/** O que vai para o ranking: o melhor de cada número de uma partida (ou do histórico) num mapa e dificuldade. */
data class Submission(
    val chartId: String,
    val difficulty: Difficulty,
    val title: String,
    val artist: String,
    val points: Long,
    val accuracy: Float,
    val combo: Int,
    val perfect: Int,
    val cleanMs: Long?,
    /** O total de notas da partida (perfeitos + bons + erros), para "perfeitos de quantos". */
    val notes: Int? = null,
)

/** Regras do nome (as mesmas do script): 3–20 letras, números, espaço, _ ou -. */
object PlayerNames {
    private val VALID = Regex("""^[\p{L}\p{N} _-]{3,20}$""")

    fun isValid(name: String): Boolean = VALID.matches(name.trim())
}
