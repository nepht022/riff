package com.rodrigomachado.riff.feature.results

import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.ScoreRepository
import com.rodrigomachado.riff.core.data.ranking.RankingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** O [ResultsViewModel] criado pelo Hilt no celular: o id da partida vem dos argumentos da rota. */
@HiltViewModel
class HiltResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    scores: ScoreRepository,
    charts: ChartRepository,
    ranking: RankingRepository,
) : ResultsViewModel(checkNotNull(savedStateHandle["scoreId"]), scores, charts, ranking)
