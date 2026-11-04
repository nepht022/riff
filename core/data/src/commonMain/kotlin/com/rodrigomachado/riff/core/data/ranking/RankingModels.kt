package com.rodrigomachado.riff.core.data.ranking

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.RankingTable

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

/**
 * Uma música com alguém no ranking. [players] e [yourPosition] somam as tabelas; [tables] diz quem está em cada uma
 * (vazio com o script de antes da 4d, em que tudo é do celular).
 */
data class RankedSong(
    val chartId: String,
    val title: String,
    val artist: String,
    val players: Int,
    val yourPosition: Int?,
    val tables: Map<RankingTable, TableStanding> = emptyMap(),
) {
    /** Quem está na [table] (nulo: ninguém). */
    fun inTable(table: RankingTable): TableStanding? = when {
        tables.isNotEmpty() -> tables[table]
        table == RankingTable.PHONE -> TableStanding(players, yourPosition)
        else -> null
    }
}

/** Uma tabela numa música: os jogadores com pontuação e a sua melhor posição (em pontos), se tiver. */
data class TableStanding(val players: Int, val yourPosition: Int?)

/** O resultado de pedir um código de vínculo ou de vincular este aparelho a um nome que já existe. */
sealed interface LinkResult {
    /** O código para digitar no outro aparelho; vale [validForSeconds] a partir de agora. */
    data class Code(val code: String, val validForSeconds: Int) : LinkResult

    /** Este aparelho passou a ser do jogador [name]. */
    data class Linked(val name: String) : LinkResult

    /** Código errado, vencido, já usado ou de alguém removido (o script não diz qual). */
    data object InvalidCode : LinkResult

    data object Limit : LinkResult

    /** O script ainda é o de antes da 4d (não conhece o vínculo): precisa ser republicado. */
    data object Outdated : LinkResult

    data object Offline : LinkResult

    /** Pedir o código sem ter nome. */
    data object NoName : LinkResult

    /** O dono tirou este aparelho do ranking. */
    data object Removed : LinkResult
}

/** O código de vínculo como o script manda: use [validForSeconds], não o relógio do aparelho. */
data class LinkCode(val code: String, val validForSeconds: Int)

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
    val table: RankingTable = RankingTable.PHONE,
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
    /** A tabela em que a partida conta. */
    val table: RankingTable = RankingTable.PHONE,
)

/** Regras do nome (as mesmas do script): 3–20 letras, números, espaço, _ ou -. */
object PlayerNames {
    private val VALID = Regex("""^[\p{L}\p{N} _-]{3,20}$""")

    fun isValid(name: String): Boolean = VALID.matches(name.trim())
}
