package com.rodrigomachado.riff.feature.ranking

import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.data.ranking.RankingRepository
import com.rodrigomachado.riff.core.model.RankingTable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** O [PlayerNameViewModel] criado pelo Hilt no celular. */
@HiltViewModel
class HiltPlayerNameViewModel @Inject constructor(ranking: RankingRepository) : PlayerNameViewModel(ranking)

/** O [RankingViewModel] criado pelo Hilt no celular: abre na tabela do celular. */
@HiltViewModel
class HiltRankingViewModel @Inject constructor(ranking: RankingRepository) :
    RankingViewModel(ranking, RankingTable.PHONE)

/**
 * O [RankingBoardViewModel] criado pelo Hilt no celular: o mapa, o título, a dificuldade e a tabela vêm da rota (sem
 * tabela, a do celular).
 */
@HiltViewModel
class HiltRankingBoardViewModel @Inject constructor(savedStateHandle: SavedStateHandle, ranking: RankingRepository) :
    RankingBoardViewModel(
        chartId = checkNotNull(savedStateHandle["chartId"]),
        title = savedStateHandle["title"] ?: "",
        difficulty = savedStateHandle["difficulty"],
        ranking = ranking,
        table = RankingTable.entries.firstOrNull { it.name == savedStateHandle.get<String>("table") }
            ?: RankingTable.PHONE,
    )
