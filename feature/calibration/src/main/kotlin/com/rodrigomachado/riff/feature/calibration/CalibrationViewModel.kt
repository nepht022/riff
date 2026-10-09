package com.rodrigomachado.riff.feature.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.data.MAX_SYNC_OFFSET_MS
import com.rodrigomachado.riff.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CalibrationUiState {
    data object Instructions : CalibrationUiState

    data class Measuring(val taps: Int) : CalibrationUiState

    data class Result(val latencyMs: Int) : CalibrationUiState

    data object Unstable : CalibrationUiState

    data object AudioFailed : CalibrationUiState
}

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val player: ClickTrackPlayer,
) : ViewModel() {
    private val state = MutableStateFlow<CalibrationUiState>(CalibrationUiState.Instructions)
    val uiState: StateFlow<CalibrationUiState> = state.asStateFlow()

    private val taps = mutableListOf<Long>()
    private var measuring: Job? = null

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // qualquer falha da saída vira a mensagem na tela
    fun start() {
        measuring?.cancel()
        taps.clear()
        state.value = CalibrationUiState.Measuring(taps = 0)
        measuring = viewModelScope.launch {
            state.value = try {
                when (val result = CalibrationSession.result(player.play(), taps.toList())) {
                    is CalibrationResult.Measured -> CalibrationUiState.Result(result.latencyMs.limited())
                    CalibrationResult.Unstable -> CalibrationUiState.Unstable
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CalibrationUiState.AudioFailed
            }
        }
    }

    /** Instante do toque no relógio `uptimeMillis` (o do evento de toque). */
    fun onTap(uptimeMs: Long) {
        val current = state.value as? CalibrationUiState.Measuring ?: return
        taps += uptimeMs
        state.value = current.copy(taps = taps.size)
    }

    /** Retoque à mão antes de salvar. */
    fun nudge(deltaMs: Int) {
        val current = state.value as? CalibrationUiState.Result ?: return
        state.value = CalibrationUiState.Result((current.latencyMs + deltaMs).limited())
    }

    fun save(onSaved: () -> Unit) {
        val current = state.value as? CalibrationUiState.Result ?: return
        viewModelScope.launch {
            settings.update { it.copy(audioOffsetMs = -current.latencyMs) }
            onSaved()
        }
    }

    /** O app saiu da tela no meio da medição: para o metrônomo e volta ao começo. */
    fun cancel() {
        if (state.value !is CalibrationUiState.Measuring) return
        measuring?.cancel()
        measuring = null
        taps.clear()
        state.value = CalibrationUiState.Instructions
    }

    private fun Int.limited(): Int = coerceIn(-MAX_SYNC_OFFSET_MS, MAX_SYNC_OFFSET_MS)
}
