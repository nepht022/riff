package com.rodrigomachado.riff.feature.ranking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.data.ranking.ApiAnswer
import com.rodrigomachado.riff.core.data.ranking.Board
import com.rodrigomachado.riff.core.data.ranking.BoardEntry
import com.rodrigomachado.riff.core.data.ranking.RankedSong
import com.rodrigomachado.riff.core.data.ranking.RankingMetric
import com.rodrigomachado.riff.core.data.ranking.RankingRepository
import com.rodrigomachado.riff.core.data.ranking.rankBoard
import com.rodrigomachado.riff.core.model.Difficulty
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RankingSongsUiState(
    val query: String = "",
    val loading: Boolean = false,
    val songs: List<RankedSong> = emptyList(),
    val offline: Boolean = false,
    val notConfigured: Boolean = false,
) {
    /** A lista vem uma vez do script; a busca (música ou artista, sem acento) é feita aqui, na hora. */
    val shown: List<RankedSong>
        get() {
            val wanted = query.folded()
            return if (wanted.isEmpty()) {
                songs
            } else {
                songs.filter {
                    "${it.title} ${it.artist}".folded().contains(
                        wanted,
                    )
                }
            }
        }
}

private val ACCENTS = Regex("\\p{Mn}+")

private fun String.folded() =
    java.text.Normalizer.normalize(trim(), java.text.Normalizer.Form.NFD).replace(ACCENTS, "").lowercase()

@HiltViewModel
class RankingViewModel @Inject constructor(private val ranking: RankingRepository) : ViewModel() {
    private val ui = MutableStateFlow(RankingSongsUiState(notConfigured = !ranking.isConfigured))
    val state: StateFlow<RankingSongsUiState> = ui

    init {
        if (ranking.isConfigured) load()
    }

    fun onQuery(query: String) = ui.update { it.copy(query = query) }

    fun load() {
        viewModelScope.launch {
            ui.update { it.copy(loading = true, offline = false) }
            when (val answer = ranking.songs("")) {
                is ApiAnswer.Ok -> ui.update { it.copy(loading = false, songs = answer.value) }
                else -> ui.update { it.copy(loading = false, offline = true) }
            }
        }
    }
}

/** [difficulty]: a pedida (rota ou toque); o [board] diz qual foi mostrada. */
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

    // Todas as linhas do mapa, de uma vez: os filtros só remontam o placar.
    private var entries: List<BoardEntry> = emptyList()

    init {
        load()
    }

    fun onDifficulty(difficulty: Difficulty) = change { it.copy(difficulty = difficulty) }

    fun onMetric(metric: RankingMetric) = change { it.copy(metric = metric) }

    fun onToggleDirection() = change { it.copy(descending = !it.descending) }

    fun onQuery(query: String) = change { it.copy(query = query) }

    fun load() {
        viewModelScope.launch {
            ui.update { it.copy(loading = true, offline = false) }
            when (val answer = ranking.board(chartId)) {
                is ApiAnswer.Ok -> {
                    entries = answer.value
                    change { it.copy(loading = false) }
                }

                else -> ui.update { it.copy(loading = false, offline = true) }
            }
        }
    }

    private fun change(transform: (RankingBoardUiState) -> RankingBoardUiState) = ui.update {
        val next = transform(it)
        if (next.loading || next.offline) return@update next
        next.copy(board = rankBoard(entries, next.difficulty, next.metric, next.descending, next.query))
    }
}
