package com.rodrigomachado.riff.core.data.queue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Partidas abertas, da criação da tela até ela sair de vez (inclusive depois de trocar de música na pausa): a fila
 * do Spotify não apaga nada enquanto houver uma (os downloads continuam, numa thread de prioridade baixa), e o mapa
 * aberto vira "jogado" (nunca é apagado).
 */
interface MatchActivity {
    val inMatch: StateFlow<Boolean>

    suspend fun enter(chartId: String)

    fun leave(chartId: String)

    fun isOpen(chartId: String): Boolean
}

@Singleton
class DefaultMatchActivity @Inject constructor(private val downloads: QueueDownloads) : MatchActivity {
    // Contagem por mapa: a próxima partida pode abrir antes de a anterior terminar de sair.
    private val open = mutableMapOf<String, Int>()
    private val active = MutableStateFlow(false)
    override val inMatch: StateFlow<Boolean> = active.asStateFlow()

    override suspend fun enter(chartId: String) {
        synchronized(open) {
            open[chartId] = (open[chartId] ?: 0) + 1
            active.value = true
        }
        downloads.markPlayed(chartId)
    }

    override fun leave(chartId: String) = synchronized(open) {
        val left = (open[chartId] ?: 0) - 1
        if (left > 0) open[chartId] = left else open.remove(chartId)
        active.value = open.isNotEmpty()
    }

    override fun isOpen(chartId: String): Boolean = synchronized(open) { chartId in open }
}
