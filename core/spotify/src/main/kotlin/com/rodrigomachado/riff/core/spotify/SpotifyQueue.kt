package com.rodrigomachado.riff.core.spotify

import kotlinx.coroutines.flow.Flow

/**
 * Uma música da fila do Spotify. A sessão de mídia não traz a duração ([durationMs] nulo): ela chega pelos metadados
 * quando a música começa a tocar.
 */
data class QueuedTrack(val uri: String, val title: String, val artist: String, val durationMs: Long?)

sealed interface QueueRead {
    data class Tracks(val tracks: List<QueuedTrack>) : QueueRead

    /** Sem sessão do Spotify agora (fechado, sem permissão): tenta na próxima leitura. */
    data object Unavailable : QueueRead
}

/** As próximas músicas da fila do Spotify. */
interface SpotifyQueue {
    suspend fun next(count: Int = 2): QueueRead

    /** Avisa quando a fila muda, para ler de novo na hora. */
    val changes: Flow<Unit>
}
