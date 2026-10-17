package com.rodrigomachado.riff.core.data.queue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Por que a fila deixou uma música sem mapa. */
enum class QueueMiss { NOTHING_FOUND, ONLY_OFFICIAL, ONLY_OTHER_DURATION, SEARCH_FAILED, DOWNLOAD_FAILED }

/** O que a fila não conseguiu, por faixa do Spotify, para o Tocando agora contar. Só na memória. */
interface QueueMisses {
    val misses: StateFlow<Map<String, QueueMiss>>

    fun record(trackUri: String, miss: QueueMiss)

    fun clear(trackUri: String)
}

@Singleton
class MemoryQueueMisses @Inject constructor() : QueueMisses {
    private val state = MutableStateFlow<Map<String, QueueMiss>>(emptyMap())
    override val misses: StateFlow<Map<String, QueueMiss>> = state.asStateFlow()

    override fun record(trackUri: String, miss: QueueMiss) = state.update { it + (trackUri to miss) }

    override fun clear(trackUri: String) = state.update { it - trackUri }
}
