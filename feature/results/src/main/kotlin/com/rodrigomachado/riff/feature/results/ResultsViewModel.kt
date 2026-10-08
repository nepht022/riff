package com.rodrigomachado.riff.feature.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.ScoreRepository
import com.rodrigomachado.riff.core.model.ScoreRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ResultsUiState {
    data object Loading : ResultsUiState

    data object Missing : ResultsUiState

    data class Ready(val record: ScoreRecord, val title: String, val artist: String, val isNewBest: Boolean) :
        ResultsUiState
}

@HiltViewModel
class ResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scores: ScoreRepository,
    private val charts: ChartRepository,
) : ViewModel() {
    private val scoreId: Long = checkNotNull(savedStateHandle["scoreId"])
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
                ResultsUiState.Ready(
                    record,
                    chart?.title.orEmpty(),
                    chart?.artist.orEmpty(),
                    isNewBest = best?.id == record.id,
                )
            }
        }
    }
}
