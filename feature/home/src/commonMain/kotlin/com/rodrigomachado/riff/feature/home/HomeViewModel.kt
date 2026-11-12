package com.rodrigomachado.riff.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.ScoreRepository
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.ScoreRecord
import com.rodrigomachado.riff.core.ui.art.ChartArtImages
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Uma partida da tela inicial: o mapa (título e artista da biblioteca), a dificuldade e o recorde nela. */
data class HomeMatch(
    val chartId: String,
    val title: String,
    val artist: String,
    val difficulty: Difficulty,
    val best: Long,
)

/** [last]: o "Continuar" (null sem partidas); [recentBests]: os "Últimos recordes" (até 3). */
data class HomeUiState(val last: HomeMatch? = null, val recentBests: List<HomeMatch> = emptyList())

/**
 * As partidas da tela inicial do celular: a última (com o recorde daquele mapa e dificuldade) e os últimos
 * recordes. Partida de um mapa que saiu da biblioteca não aparece (não há o que abrir). [images]: as capas. Quem lê o
 * banco é o [refresh], chamado pela tela ao abrir e ao voltar a ela (uma consulta só ao abrir).
 */
open class HomeViewModel(
    private val scores: ScoreRepository,
    private val charts: ChartRepository,
    val images: ChartArtImages?,
) : ViewModel() {
    private val state = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = state.asStateFlow()
    private var loading: Job? = null

    /** Lê o banco: a tela chama ao abrir e ao voltar a ela (depois de uma partida, o "Continuar" já é a nova). */
    fun refresh() {
        loading?.cancel()
        loading = viewModelScope.launch {
            val last = scores.last()?.let { record ->
                match(record, best = scores.best(record.chartId, record.difficulty)?.score ?: record.score)
            }
            val bests = scores.recentBests(RECENT_BESTS).mapNotNull { match(it, best = it.score) }
            state.value = HomeUiState(last, bests)
        }
    }

    private suspend fun match(record: ScoreRecord, best: Long): HomeMatch? =
        charts.getChart(record.chartId)?.let { HomeMatch(it.id, it.title, it.artist, record.difficulty, best) }

    companion object {
        /** Quantos "Últimos recordes" a tela mostra. */
        const val RECENT_BESTS = 3
    }
}
