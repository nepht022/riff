package com.rodrigomachado.riff.feature.ranking

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
import com.rodrigomachado.riff.core.model.RankingTable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RankingSongsUiState(
    val query: String = "",
    val loading: Boolean = false,
    val songs: List<RankedSong> = emptyList(),
    val offline: Boolean = false,
    val notConfigured: Boolean = false,
    /** A tabela escolhida: a lista mostra só as músicas com alguém nela. */
    val table: RankingTable = RankingTable.PHONE,
) {
    /** As músicas com alguém na [table]: a lista vem uma vez do script e as tabelas e a busca são filtradas aqui. */
    private val inTable: List<RankedSong>
        get() = songs.filter { it.inTable(table) != null }

    /** As da [table] que batem com a busca (música ou artista, sem acento). */
    val shown: List<RankedSong>
        get() {
            val wanted = query.folded()
            if (wanted.isEmpty()) return inTable
            return inTable.filter { "${it.title} ${it.artist}".folded().contains(wanted) }
        }

    /** Há músicas no ranking, mas ninguém na [table] ainda. */
    val tableEmpty: Boolean
        get() = songs.isNotEmpty() && inTable.isEmpty()
}

private val ACCENTS = Regex("\\p{Mn}+")

private fun String.folded() =
    java.text.Normalizer.normalize(trim(), java.text.Normalizer.Form.NFD).replace(ACCENTS, "").lowercase()

/**
 * As músicas do ranking, abertas na [table] do aparelho (vem de fora: o PC diz a do modo atual); no celular o Hilt
 * cria o [HiltRankingViewModel].
 */
open class RankingViewModel(private val ranking: RankingRepository, table: RankingTable = RankingTable.PHONE) :
    ViewModel() {
    private val ui = MutableStateFlow(RankingSongsUiState(notConfigured = !ranking.isConfigured, table = table))
    val state: StateFlow<RankingSongsUiState> = ui

    init {
        if (ranking.isConfigured) load()
    }

    fun onQuery(query: String) = ui.update { it.copy(query = query) }

    /** A lista já tem todas as tabelas: trocar não volta ao script. */
    fun onTable(table: RankingTable) = ui.update { it.copy(table = table) }

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
    val table: RankingTable = RankingTable.PHONE,
)

/**
 * O placar do mapa [chartId], aberto na [difficulty] pedida (nome do enum, como na [RankingBoardRoute]) e na [table]
 * que vem de fora (a do aparelho, ou a escolhida na lista); no celular o Hilt cria o [HiltRankingBoardViewModel] com
 * os argumentos da rota.
 */
open class RankingBoardViewModel(
    private val chartId: String,
    title: String,
    difficulty: String?,
    private val ranking: RankingRepository,
    table: RankingTable = RankingTable.PHONE,
) : ViewModel() {
    private val ui = MutableStateFlow(
        RankingBoardUiState(
            title = title,
            difficulty = difficulty?.let { name -> Difficulty.entries.firstOrNull { it.name == name } },
            table = table,
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

    /** Todas as tabelas vêm juntas do script: trocar só remonta o placar. */
    fun onTable(table: RankingTable) = change { it.copy(table = table) }

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
        val inTable = entries.filter { entry -> entry.table == next.table }
        next.copy(board = rankBoard(inTable, next.difficulty, next.metric, next.descending, next.query))
    }
}
