package com.rodrigomachado.riff.core.data.queue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Partida em andamento: a fila não baixa nada (rede, disco e CPU ficam para a pista); o mapa jogado fica. */
interface MatchActivity {
    val inMatch: StateFlow<Boolean>

    /** A partida começou com [chartId]: se veio da fila, passa a ser um mapa normal. */
    suspend fun started(chartId: String)

    fun ended()
}

@Singleton
class DefaultMatchActivity @Inject constructor(private val downloads: QueueDownloads) : MatchActivity {
    private val active = MutableStateFlow(false)
    override val inMatch: StateFlow<Boolean> = active.asStateFlow()

    override suspend fun started(chartId: String) {
        active.value = true
        downloads.markPlayed(chartId)
    }

    override fun ended() {
        active.value = false
    }
}
