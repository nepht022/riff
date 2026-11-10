package com.rodrigomachado.riff.feature.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.room.NoConnection
import com.rodrigomachado.riff.core.room.Room
import com.rodrigomachado.riff.core.room.RoomRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface RoomLobbyUiState {
    data object Loading : RoomLobbyUiState

    data class Ready(val room: Room) : RoomLobbyUiState

    /** A sala não existe mais (o último saiu, ou ela morreu). */
    data object Ended : RoomLobbyUiState

    /** Sem internet para entrar no Firebase: tenta de novo sozinho. */
    data object NoConnection : RoomLobbyUiState
}

/**
 * A sala [code] ao vivo. Enquanto a tela está aberta (alguém olha o [uiState]), segue a sala pelo streaming e manda o
 * batimento a cada 20 s; com o app em segundo plano, os dois param (e o jogador some para os outros em 60 s). No
 * celular, o Hilt cria com o código da rota.
 */
open class RoomLobbyViewModel(private val code: String, private val repository: RoomRepository) : ViewModel() {
    private val leaving = MutableStateFlow(false)
    private val done = MutableStateFlow(false)

    /** Saiu da sala: a tela volta. */
    val left: StateFlow<Boolean> = done

    val uiState: StateFlow<RoomLobbyUiState> = channelFlow {
        launch { heartbeats() }
        repository.observe(code)
            .map { room -> room?.let { RoomLobbyUiState.Ready(it) } ?: RoomLobbyUiState.Ended }
            .retryWhen { cause, _ ->
                val offline = cause is NoConnection
                if (offline) {
                    emit(RoomLobbyUiState.NoConnection)
                    delay(RETRY_MS)
                }
                offline
            }
            .collect { send(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), RoomLobbyUiState.Loading)

    /** Sai da sala (o dono passa adiante; o último apaga a sala) e então avisa a tela pelo [left]. */
    fun leave() {
        if (leaving.value) return
        leaving.value = true
        viewModelScope.launch {
            repository.leave(code)
            done.value = true
        }
    }

    private suspend fun heartbeats() {
        while (true) {
            if (!leaving.value) repository.heartbeat(code)
            delay(HEARTBEAT_MS)
        }
    }

    private companion object {
        const val HEARTBEAT_MS = 20_000L
        const val RETRY_MS = 5_000L

        // Girar a tela não para o streaming; o app em segundo plano, sim.
        const val STOP_MS = 5_000L
    }
}
