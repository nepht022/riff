package com.rodrigomachado.riff.core.data.queue

import com.rodrigomachado.riff.core.database.QueueDownloadDao
import com.rodrigomachado.riff.core.database.QueueDownloadEntity
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

data class QueueDownload(val chartId: String, val trackUri: String, val downloadedAt: Long, val played: Boolean)

/** Mapas baixados sozinhos pela fila do Spotify que ainda podem ser apagados (a linha some com o mapa). */
interface QueueDownloads {
    suspend fun add(chartId: String, trackUri: String, at: Long)

    /** Jogado: deixa de ser "da fila" para sempre. */
    suspend fun markPlayed(chartId: String)

    suspend fun unplayed(): List<QueueDownload>

    suspend fun remove(chartIds: Collection<String>)

    suspend fun forTrack(trackUri: String): QueueDownload?
}

class RoomQueueDownloads @Inject constructor(private val dao: QueueDownloadDao) : QueueDownloads {
    override suspend fun add(chartId: String, trackUri: String, at: Long) =
        dao.upsert(QueueDownloadEntity(chartId, trackUri, at, played = false))

    override suspend fun markPlayed(chartId: String) = dao.markPlayed(chartId)

    override suspend fun unplayed(): List<QueueDownload> = dao.unplayed().map { it.toModel() }

    override suspend fun remove(chartIds: Collection<String>) = dao.delete(chartIds.toList())

    override suspend fun forTrack(trackUri: String): QueueDownload? = dao.forTrack(trackUri)?.toModel()

    private fun QueueDownloadEntity.toModel() = QueueDownload(chartId, trackUri, downloadedAtEpochMs, played)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class QueueDownloadsModule {
    @Binds
    abstract fun queueDownloads(impl: RoomQueueDownloads): QueueDownloads
}
