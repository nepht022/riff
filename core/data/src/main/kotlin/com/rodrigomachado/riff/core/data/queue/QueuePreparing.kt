package com.rodrigomachado.riff.core.data.queue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * As faixas da fila que ainda podem ganhar um mapa: esperando a vez, pesquisando ou baixando. O Tocando agora
 * espera por elas antes de decidir (spec Spotify contínuo §3).
 */
interface QueuePreparing {
    val tracks: StateFlow<Set<String>>

    fun start(trackUri: String)

    fun done(trackUri: String)
}

@Singleton
class MemoryQueuePreparing @Inject constructor() : QueuePreparing {
    private val state = MutableStateFlow<Set<String>>(emptySet())
    override val tracks: StateFlow<Set<String>> = state.asStateFlow()

    override fun start(trackUri: String) = state.update { it + trackUri }

    override fun done(trackUri: String) = state.update { it - trackUri }
}
