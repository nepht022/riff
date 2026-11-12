package com.rodrigomachado.riff.feature.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.ScoreRepository
import com.rodrigomachado.riff.core.data.ranking.RankingRepository
import com.rodrigomachado.riff.core.data.ranking.SubmitStatus
import com.rodrigomachado.riff.core.model.ScoreRecord
import com.rodrigomachado.riff.core.ui.art.ChartArtImages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ResultsUiState {
    data object Loading : ResultsUiState

    data object Missing : ResultsUiState

    /**
     * [ranking]: o que aconteceu com a partida no ranking; [rankingOn]: o ranking está configurado no build;
     * [previousBest]: com recorde novo, os pontos do recorde de antes desta partida (nulo: era o primeiro).
     */
    data class Ready(
        val record: ScoreRecord,
        val title: String,
        val artist: String,
        val isNewBest: Boolean,
        val ranking: SubmitStatus? = null,
        val rankingOn: Boolean = false,
        val previousBest: Long? = null,
    ) : ResultsUiState
}

/**
 * O resultado da partida [scoreId]; no celular o [HiltResultsViewModel] lê o id dos argumentos da rota. [images]: a
 * capa do mapa (as pequenas, guardadas na memória).
 */
open class ResultsViewModel(
    private val scoreId: Long,
    private val scores: ScoreRepository,
    private val charts: ChartRepository,
    private val ranking: RankingRepository,
    val images: ChartArtImages?,
) : ViewModel() {
    private val state = MutableStateFlow<ResultsUiState>(ResultsUiState.Loading)
    val uiState: StateFlow<ResultsUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val record = scores.get(scoreId)
            state.value = if (record == null) {
                ResultsUiState.Missing
            } else {
                val chart = charts.getChart(record.chartId)
                val best = scores.best(record.chartId, record.difficulty)
                val isNewBest = best?.id == record.id
                ResultsUiState.Ready(
                    record,
                    chart?.title.orEmpty(),
                    chart?.artist.orEmpty(),
                    isNewBest = isNewBest,
                    rankingOn = ranking.isConfigured,
                    // O "antes: N" só acompanha o recorde novo; as partidas que vieram depois desta não contam.
                    previousBest = if (isNewBest) {
                        scores.bestBefore(record.chartId, record.difficulty, record.id)?.score
                    } else {
                        null
                    },
                )
            }
            // O envio segue depois da partida (enviando → posição): a tela acompanha.
            ranking.statusFor(scoreId).collect { status ->
                state.update { (it as? ResultsUiState.Ready)?.copy(ranking = status) ?: it }
            }
        }
    }
}
