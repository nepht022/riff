package com.rodrigomachado.riff.feature.ranking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.data.ranking.ApiAnswer
import com.rodrigomachado.riff.core.data.ranking.Board
import com.rodrigomachado.riff.core.data.ranking.RankedSong
import com.rodrigomachado.riff.core.data.ranking.RankingMetric
import com.rodrigomachado.riff.core.data.ranking.RankingRepository
import com.rodrigomachado.riff.core.model.Difficulty
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Espera parar de digitar antes de perguntar ao servidor. */
private const val TYPING_PAUSE_MS = 300L

data class RankingSongsUiState(
    val query: String = "",
    val loading: Boolean = false,
    val songs: List<RankedSong> = emptyList(),
    val offline: Boolean = false,
    val notConfigured: Boolean = false,
)

@HiltViewModel
class RankingViewModel @Inject constructor(private val ranking: RankingRepository) : ViewModel() {
    private val ui = MutableStateFlow(RankingSongsUiState(notConfigured = !ranking.isConfigured))
    val state: StateFlow<RankingSongsUiState> = ui
    private var search: Job? = null

    init {
        if (ranking.isConfigured) load()
    }

    fun onQuery(query: String) {
        ui.update { it.copy(query = query) }
        search?.cancel()
        search = viewModelScope.launch {
            delay(TYPING_PAUSE_MS)
            fetch(query)
        }
    }

    fun load() {
        search?.cancel()
        search = viewModelScope.launch { fetch(ui.value.query) }
    }

    private suspend fun fetch(query: String) {
        ui.update { it.copy(loading = true, offline = false) }
        when (val answer = ranking.songs(query)) {
            is ApiAnswer.Ok -> ui.update { it.copy(loading = false, songs = answer.value) }
            else -> ui.update { it.copy(loading = false, offline = true) }
        }
    }
}

data class RankingBoardUiState(
    val title: String = "",
    val difficulty: Difficulty? = null,
    val metric: RankingMetric = RankingMetric.POINTS,
    val descending: Boolean = true,
    val query: String = "",
    val board: Board? = null,
    val loading: Boolean = false,
    val offline: Boolean = false,
)

@HiltViewModel
class RankingBoardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ranking: RankingRepository,
) : ViewModel() {
    private val chartId: String = checkNotNull(savedStateHandle["chartId"])
    private val ui = MutableStateFlow(
        RankingBoardUiState(
            title = savedStateHandle["title"] ?: "",
            difficulty = savedStateHandle.get<String>("difficulty")?.let { name ->
                Difficulty.entries.firstOrNull { it.name == name }
            },
        ),
    )
    val state: StateFlow<RankingBoardUiState> = ui
    private var loading: Job? = null

    init {
        load()
    }

    fun onDifficulty(difficulty: Difficulty) = change { it.copy(difficulty = difficulty) }

    fun onMetric(metric: RankingMetric) = change { it.copy(metric = metric) }

    fun onToggleDirection() = change { it.copy(descending = !it.descending) }

    fun onQuery(query: String) {
        ui.update { it.copy(query = query) }
        load(waitForTyping = true)
    }

    fun load(waitForTyping: Boolean = false) {
        loading?.cancel()
        loading = viewModelScope.launch {
            if (waitForTyping) delay(TYPING_PAUSE_MS)
            val asked = ui.value
            ui.update { it.copy(loading = true, offline = false) }
            val answer = ranking.board(chartId, asked.difficulty, asked.metric, asked.descending, asked.query)
            ui.update {
                when (answer) {
                    // O servidor diz em que dificuldade abriu (a primeira que tem, se a pedida não tiver).
                    is ApiAnswer.Ok -> it.copy(
                        loading = false,
                        board = answer.value,
                        difficulty = answer.value.difficulty ?: it.difficulty,
                    )

                    else -> it.copy(loading = false, offline = true)
                }
            }
        }
    }

    private fun change(transform: (RankingBoardUiState) -> RankingBoardUiState) {
        ui.update(transform)
        load()
    }
}
