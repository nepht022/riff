package com.rodrigomachado.riff.feature.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.room.JoinResult
import com.rodrigomachado.riff.core.room.RoomCodes
import com.rodrigomachado.riff.core.room.RoomRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [code]: o digitado, já normalizado; [joined]: abrir a sala com este código. */
data class RoomJoinUiState(
    val code: String = "",
    val joining: Boolean = false,
    val error: RoomError? = null,
    val askName: Boolean = false,
    val joined: String? = null,
) {
    val canJoin: Boolean get() = code.length == RoomCodes.LENGTH && !joining
}

/** Entrar numa sala pelo código, com o nome do ranking ([playerName]); no celular, o Hilt cria. */
open class RoomJoinViewModel(private val repository: RoomRepository, playerName: Flow<String?>) : ViewModel() {
    private val ui = MutableStateFlow(RoomJoinUiState())
    val state: StateFlow<RoomJoinUiState> = ui
    private val nameGate = NameGate(viewModelScope, playerName)

    /**
     * Guarda só o que pode virar código: maiúsculas, sem espaços, até 4 letras. Uma letra fora do alfabeto do código
     * (`I`, `L`, `O`, números…) é recusada: o campo fica como estava.
     */
    fun onCode(input: String) {
        val code = input.filterNot { it.isWhitespace() }.uppercase()
        if (code.any { it !in RoomCodes.ALPHABET }) return
        ui.update { it.copy(code = code.take(RoomCodes.LENGTH), error = null) }
    }

    fun join() {
        val code = RoomCodes.normalize(ui.value.code) ?: return
        if (ui.value.joining) return
        ui.update { it.copy(joining = true, error = null) }
        viewModelScope.launch {
            nameGate.run(onAsk = { ui.update { it.copy(joining = false, askName = true) } }) { name ->
                ui.update { it.copy(joining = true) }
                val result = repository.join(code, name)
                ui.update {
                    when (result) {
                        is JoinResult.Joined -> it.copy(joining = false, joined = result.code)
                        JoinResult.NotFound -> it.copy(joining = false, error = RoomError.NOT_FOUND)
                        JoinResult.Full -> it.copy(joining = false, error = RoomError.FULL)
                        JoinResult.NoConnection -> it.copy(joining = false, error = RoomError.NO_CONNECTION)
                    }
                }
            }
        }
    }

    /** O app já abriu o diálogo do nome. */
    fun onAskedName() = ui.update { it.copy(askName = false) }

    /** O app já abriu a sala. */
    fun onOpened() = ui.update { it.copy(joined = null) }
}
