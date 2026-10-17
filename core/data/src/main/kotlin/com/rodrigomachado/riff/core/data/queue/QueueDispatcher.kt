package com.rodrigomachado.riff.core.data.queue

import android.os.Process
import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.EncoreDownloads
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.TrackMatchRepository
import com.rodrigomachado.riff.core.data.download.ChartDownloader
import com.rodrigomachado.riff.core.data.download.EncoreChartDownloader
import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.data.folder.FolderSource
import com.rodrigomachado.riff.core.data.folder.FolderWriter
import com.rodrigomachado.riff.core.data.importer.FolderChartImporter
import com.rodrigomachado.riff.core.encore.EncoreClient
import com.rodrigomachado.riff.core.encore.UrlConnectionTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Uma thread em prioridade baixa para os downloads da fila: eles acontecem durante a partida, e a pista vem antes
 * (spec Spotify contínuo §6). Uma só basta: a fila baixa um mapa por vez.
 */
fun queueDispatcher(): CoroutineDispatcher = Executors.newSingleThreadExecutor { task ->
    Thread(
        {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        },
        "riff-fila",
    ).apply { isDaemon = true }
}.asCoroutineDispatcher()

/** O downloader da fila: o mesmo do Encore, com rede, importação e gravação na thread de [queueDispatcher]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class QueueDownloader

@Module
@InstallIn(SingletonComponent::class)
object QueueDownloaderModule {
    @Provides
    @Singleton
    @QueueDownloader
    @Suppress("LongParameterList") // as mesmas dependências do downloader de sempre, só com outra thread
    fun queueChartDownloader(
        folders: LibraryFolderStore,
        access: FolderAccess,
        writer: FolderWriter,
        source: FolderSource,
        charts: ChartRepository,
        downloads: EncoreDownloads,
        matches: TrackMatchRepository,
        opener: RandomReadOpener,
    ): ChartDownloader {
        val background = queueDispatcher()
        val importer = FolderChartImporter(
            source,
            charts,
            background,
            FolderChartImporter.MAX_FILE_BYTES,
            System::currentTimeMillis,
        )
        return EncoreChartDownloader(
            EncoreClient(UrlConnectionTransport(), background),
            folders,
            access,
            writer,
            importer,
            downloads,
            matches,
            background,
            opener,
        )
    }
}
