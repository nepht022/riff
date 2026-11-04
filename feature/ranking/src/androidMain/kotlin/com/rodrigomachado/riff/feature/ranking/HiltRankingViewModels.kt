package com.rodrigomachado.riff.feature.ranking

import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.data.ranking.RankingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** O [PlayerNameViewModel] criado pelo Hilt no celular. */
@HiltViewModel
class HiltPlayerNameViewModel @Inject constructor(ranking: RankingRepository) : PlayerNameViewModel(ranking)

/** O [RankingViewModel] criado pelo Hilt no celular. */
@HiltViewModel
class HiltRankingViewModel @Inject constructor(ranking: RankingRepository) : RankingViewModel(ranking)

/** O [RankingBoardViewModel] criado pelo Hilt no celular: o mapa, o título e a dificuldade vêm da rota. */
@HiltViewModel
class HiltRankingBoardViewModel @Inject constructor(savedStateHandle: SavedStateHandle, ranking: RankingRepository) :
    RankingBoardViewModel(
        chartId = checkNotNull(savedStateHandle["chartId"]),
        title = savedStateHandle["title"] ?: "",
        difficulty = savedStateHandle["difficulty"],
        ranking = ranking,
    )
