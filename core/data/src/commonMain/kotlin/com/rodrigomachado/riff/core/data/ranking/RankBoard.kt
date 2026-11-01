package com.rodrigomachado.riff.core.data.ranking

import com.rodrigomachado.riff.core.model.Difficulty

/**
 * O placar de uma dificuldade, montado no celular a partir de todas as linhas do mapa: trocar de número, de sentido
 * ou buscar um nome não volta ao script. As dificuldades vêm na ordem do jogo; sem [difficulty] (ou uma que o mapa
 * não tem), a primeira. Quem não tem o número fica de fora; a busca mantém a posição real.
 */
fun rankBoard(
    entries: List<BoardEntry>,
    difficulty: Difficulty?,
    metric: RankingMetric,
    descending: Boolean,
    query: String,
): Board {
    val difficulties = entries.map { it.difficulty }.distinct().sortedBy { it.ordinal }
    val chosen = difficulty?.takeIf { it in difficulties } ?: difficulties.firstOrNull()
    val ranked = entries.filter { it.difficulty == chosen && it.value(metric) != null }
        .sortedWith(compareBy<BoardEntry> { it.value(metric) }.let { if (descending) it.reversed() else it })
    val wanted = query.trim().lowercase()
    val lines = ranked.mapIndexedNotNull { index, entry ->
        if (wanted.isNotEmpty() && wanted !in entry.name.lowercase()) return@mapIndexedNotNull null
        BoardLine(
            index + 1,
            entry.name,
            entry.value(metric) ?: 0.0,
            entry.you,
            entry.notes.takeIf { metric == RankingMetric.PERFECT },
        )
    }
    return Board(difficulties, chosen, ranked.size, lines)
}
