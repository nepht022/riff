package com.rodrigomachado.riff.core.data.queue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/** Conta os preparos de cada faixa: a mesma enfileirada de novo durante a pesquisa só sai quando os dois acabam. */
@Singleton
class MemoryQueuePreparing @Inject constructor() : QueuePreparing {
    private val counts = HashMap<String, Int>()
    private val state = MutableStateFlow<Set<String>>(emptySet())
    override val tracks: StateFlow<Set<String>> = state.asStateFlow()

    override fun start(trackUri: String) = change(trackUri, +1)

    override fun done(trackUri: String) = change(trackUri, -1)

    private fun change(trackUri: String, delta: Int) = synchronized(counts) {
        val count = (counts[trackUri] ?: 0) + delta
        if (count > 0) counts[trackUri] = count else counts.remove(trackUri)
        state.value = counts.keys.toSet()
    }
}
