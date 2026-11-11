package com.rodrigomachado.riff.feature.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.room.BoardLine
import com.rodrigomachado.riff.core.room.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface RoomResultsUiState {
    /** Ainda sem a sala (a presença lendo). */
    data object Loading : RoomResultsUiState

    /**
     * A classificação da rodada [round] ([lines], na ordem do placar: pontos, maior combo, entrada; quem saiu por
     * último), com a minha linha ([me], o uid). Vazia: o banco não guarda mais esta rodada (o dono apaga a de duas
     * rodadas atrás).
     */
    data class Ready(val lines: List<BoardLine>, val me: String, val round: Int) : RoomResultsUiState {
        /** Alguém ainda terminando: nem terminou, nem saiu, nem está sem conexão (o placar dele vai chegar). */
        val waiting: Boolean get() = lines.any { !it.finished && !it.left && !it.stale }
    }
}

/**
 * O Resultado da sala [code], rodada [round] (Decisão 4 do plano da M3): a classificação final de todos, lida da sala
 * que a presença já observa ([rooms]: `RoomPresence.room`, sem abrir outro streaming). Atualiza ao vivo: o placar
 * final de quem atrasou aparece quando chega. Sem a sala por um instante (a presença trocando, ou em segundo plano),
 * fica a última classificação vista; a de outra sala é ignorada.
 */
open class RoomResultsViewModel(private val code: String, private val round: Int, rooms: Flow<Room?>) : ViewModel() {
    val uiState: StateFlow<RoomResultsUiState> = rooms
        .filterNotNull()
        .map { room -> if (room.code == code) results(room) else null }
        .filterNotNull()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), RoomResultsUiState.Loading)

    private fun results(room: Room) = RoomResultsUiState.Ready(room.boardOf(round), room.me, round)

    private companion object {
        // Girar a tela não para de ler a sala; o app em segundo plano, sim.
        const val STOP_MS = 5_000L
    }
}
