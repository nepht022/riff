package com.rodrigomachado.riff.feature.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.room.BoardLine
import com.rodrigomachado.riff.core.room.Room
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

sealed interface RoomResultsUiState {
    /** Ainda sem a sala (a presença lendo). */
    data object Loading : RoomResultsUiState

    /**
     * A classificação da rodada [round] ([lines], na ordem do placar: pontos, maior combo, entrada; quem saiu por
     * último), com a minha linha ([me], o uid). Vazia: o banco não guarda mais esta rodada (o dono apaga a de duas
     * rodadas atrás). [waiting]: a rodada ainda não acabou ([Room.roundOver]) e alguém ainda está terminando (nem
     * terminou, nem saiu, nem está sem conexão: o placar dele vai chegar).
     */
    data class Ready(
        val lines: List<BoardLine>,
        val me: String,
        val round: Int,
        val waiting: Boolean = lines.any(::stillPlaying),
    ) : RoomResultsUiState
}

/** Ainda terminando a rodada: nem terminou, nem saiu, nem está sem conexão. */
private fun stillPlaying(line: BoardLine) = !line.finished && !line.left && !line.stale

/**
 * O Resultado da sala [code], rodada [round] (Decisão 4 do plano da M3): a classificação final de todos, lida da sala
 * que a presença já observa ([rooms]: `RoomPresence.room`, sem abrir outro streaming). Atualiza ao vivo: o placar
 * final de quem atrasou aparece quando chega. Sem a sala por um instante (a presença trocando, ou em segundo plano),
 * fica a última classificação vista; a de outra sala é ignorada. Espera ([RoomResultsUiState.Ready.waiting]) até a
 * rodada acabar ([Room.roundOver], na hora do servidor [serverNow]: `RoomRepository.serverNowOrNull`; sem ela, só
 * pelos que terminaram ou saíram); a de uma rodada que já passou não espera.
 */
open class RoomResultsViewModel(
    private val code: String,
    private val round: Int,
    rooms: Flow<Room?>,
    private val serverNow: () -> Long? = { null },
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class) // transformLatest: a sala nova cancela a conferência da anterior
    val uiState: StateFlow<RoomResultsUiState> = rooms
        .filterNotNull()
        .filter { it.code == code }
        .transformLatest { room ->
            // A sala igual não chega de novo (o fim pelo tempo não muda nada no banco): enquanto espera, confere.
            while (true) {
                val ready = results(room)
                emit(ready)
                if (!ready.waiting) break
                delay(RECHECK_MS)
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), RoomResultsUiState.Loading)

    private fun results(room: Room): RoomResultsUiState.Ready {
        val lines = room.boardOf(round)
        val over = room.round != round || room.roundOver(serverNow() ?: Long.MIN_VALUE)
        return RoomResultsUiState.Ready(lines, room.me, round, waiting = !over && lines.any(::stillPlaying))
    }

    private companion object {
        // Girar a tela não para de ler a sala; o app em segundo plano, sim.
        const val STOP_MS = 5_000L

        /** De quanto em quanto o Resultado que espera confere se a rodada acabou pelo tempo. */
        const val RECHECK_MS = 1_000L
    }
}
