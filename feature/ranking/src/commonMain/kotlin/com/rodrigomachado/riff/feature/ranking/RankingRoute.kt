package com.rodrigomachado.riff.feature.ranking

import kotlinx.serialization.Serializable

/** As músicas que têm pontuação no ranking. */
@Serializable
data object RankingRoute

/**
 * O placar de um mapa; [difficulty] (nome do enum) abre nessa dificuldade, senão na primeira que tiver. [table] (nome
 * do enum `RankingTable`) abre nessa tabela, senão na do aparelho.
 */
@Serializable
data class RankingBoardRoute(
    val chartId: String,
    val title: String,
    val difficulty: String? = null,
    val table: String? = null,
)
