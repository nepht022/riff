package com.rodrigomachado.riff.core.spotify.session

import com.rodrigomachado.riff.core.spotify.QueueRead
import com.rodrigomachado.riff.core.spotify.QueuedTrack
import com.rodrigomachado.riff.core.spotify.SpotifyQueue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** A fila pela sessão de mídia: os itens depois do que está tocando, só músicas (episódios ficam de fora). */
class SessionSpotifyQueue @Inject constructor(private val session: SpotifySession) : SpotifyQueue {
    override suspend fun next(count: Int): QueueRead {
        val items = session.queue.value
        val active = items.indexOfFirst { it.queueId == session.activeQueueId.value }
        // Sem saber o que toca (Spotify fechado, rádio): não é fila vazia, senão a limpeza apagaria os mapas das próximas.
        if (active < 0) return QueueRead.Unavailable
        return QueueRead.Tracks(
            items.drop(active + 1)
                .filter { it.mediaId.isTrack() }
                .take(count)
                .map { QueuedTrack(it.mediaId.orEmpty(), it.title.orEmpty(), it.artist.orEmpty(), durationMs = null) },
        )
    }

    // Sem o valor de agora: só as mudanças depois de começar a ouvir.
    override val changes: Flow<Unit> =
        combine(session.queue, session.activeQueueId) { _, _ -> }.drop(1).map { }
}
