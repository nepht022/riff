package com.rodrigomachado.riff.feature.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.room.CreateResult
import com.rodrigomachado.riff.core.room.RoomRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [creating]: esperando o Firebase; [askName]: pedir o nome ao app; [created]: abrir a sala com este código. */
data class RoomEntryUiState(
    val creating: Boolean = false,
    val error: RoomError? = null,
    val askName: Boolean = false,
    val created: String? = null,
)

/** A escolha da Sala: criar uma sala nova com o nome do ranking ([playerName]); no celular, o Hilt cria. */
open class RoomEntryViewModel(private val repository: RoomRepository, playerName: Flow<String?>) : ViewModel() {
    private val ui = MutableStateFlow(RoomEntryUiState())
    val state: StateFlow<RoomEntryUiState> = ui
    private val nameGate = NameGate(viewModelScope, playerName)

    fun create() {
        if (ui.value.creating) return
        ui.update { it.copy(creating = true, error = null) }
        viewModelScope.launch {
            nameGate.run(onAsk = { ui.update { it.copy(creating = false, askName = true) } }) { name ->
                ui.update { it.copy(creating = true) }
                val result = repository.create(name)
                ui.update {
                    when (result) {
                        is CreateResult.Created -> it.copy(creating = false, created = result.code)
                        CreateResult.NoConnection -> it.copy(creating = false, error = RoomError.NO_CONNECTION)
                    }
                }
            }
        }
    }

    /** O app já abriu o diálogo do nome. */
    fun onAskedName() = ui.update { it.copy(askName = false) }

    /** O app já abriu a sala criada. */
    fun onOpened() = ui.update { it.copy(created = null) }
}
