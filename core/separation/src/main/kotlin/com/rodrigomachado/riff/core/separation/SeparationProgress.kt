package com.rodrigomachado.riff.core.separation

import com.rodrigomachado.riff.core.model.SeparationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** O estado da separação para o app inteiro (o Worker roda no mesmo processo). */
@Singleton
class SeparationProgress @Inject constructor() {
    private val state = MutableStateFlow<SeparationStatus>(SeparationStatus.Idle)
    val status: StateFlow<SeparationStatus> = state.asStateFlow()

    fun set(status: SeparationStatus) {
        state.value = status
    }
}
