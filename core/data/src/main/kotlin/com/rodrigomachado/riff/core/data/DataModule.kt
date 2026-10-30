package com.rodrigomachado.riff.core.data

import android.content.Context
import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
import com.rodrigomachado.riff.core.data.download.ChartDownloader
import com.rodrigomachado.riff.core.data.download.EncoreChartDownloader
import com.rodrigomachado.riff.core.data.folder.ContentRandomReadOpener
import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.data.folder.FolderSource
import com.rodrigomachado.riff.core.data.folder.FolderWriter
import com.rodrigomachado.riff.core.data.folder.SafFolderAccess
import com.rodrigomachado.riff.core.data.folder.SafFolderSource
import com.rodrigomachado.riff.core.data.folder.SafFolderWriter
import com.rodrigomachado.riff.core.data.folder.SngFolderSource
import com.rodrigomachado.riff.core.data.game.ChartArtLoader
import com.rodrigomachado.riff.core.data.game.ChartAudioProbe
import com.rodrigomachado.riff.core.data.game.ChartTrackLoader
import com.rodrigomachado.riff.core.data.game.FolderChartArtLoader
import com.rodrigomachado.riff.core.data.game.FolderChartAudioProbe
import com.rodrigomachado.riff.core.data.game.FolderChartTrackLoader
import com.rodrigomachado.riff.core.data.importer.ChartImporter
import com.rodrigomachado.riff.core.data.importer.FolderChartImporter
import com.rodrigomachado.riff.core.data.library.ChartDeleter
import com.rodrigomachado.riff.core.data.library.FolderChartDeleter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindsModule {
    @Binds
    abstract fun chartRepository(impl: RoomChartRepository): ChartRepository

    @Binds
    abstract fun libraryFolderStore(impl: PreferencesLibraryFolderStore): LibraryFolderStore

    @Binds
    abstract fun chartTrackLoader(impl: FolderChartTrackLoader): ChartTrackLoader

    @Binds
    abstract fun scoreRepository(impl: RoomScoreRepository): ScoreRepository

    @Binds
    abstract fun settingsRepository(impl: PreferencesSettingsRepository): SettingsRepository

    @Binds
    abstract fun trackMatchRepository(impl: RoomTrackMatchRepository): TrackMatchRepository

    @Binds
    abstract fun nowPlayingRepository(impl: SpotifyNowPlayingRepository): NowPlayingRepository

    @Binds
    abstract fun chartOffsetRepository(impl: RoomChartOffsetRepository): ChartOffsetRepository

    @Binds
    abstract fun encoreDownloads(impl: RoomEncoreDownloads): EncoreDownloads

    @Binds
    abstract fun aiSeparations(impl: RoomAiSeparationRepository): AiSeparationRepository

    @Binds
    abstract fun chartAudioProbe(impl: FolderChartAudioProbe): ChartAudioProbe
}

/** Acesso à pasta da biblioteca pelo SAF: permissão, leitura por posição (`.sng`), gravação (downloads) e exclusão. */
@Module
@InstallIn(SingletonComponent::class)
abstract class FolderBindsModule {
    @Binds
    abstract fun folderAccess(impl: SafFolderAccess): FolderAccess

    @Binds
    abstract fun randomReadOpener(impl: ContentRandomReadOpener): RandomReadOpener

    @Binds
    abstract fun folderWriter(impl: SafFolderWriter): FolderWriter

    @Binds
    abstract fun chartDownloader(impl: EncoreChartDownloader): ChartDownloader

    @Binds
    abstract fun chartDeleter(impl: FolderChartDeleter): ChartDeleter

    @Binds
    abstract fun chartArtLoader(impl: FolderChartArtLoader): ChartArtLoader
}

@Module
@InstallIn(SingletonComponent::class)
object DataProvidesModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @SeparationsDir
    fun separationsDir(@ApplicationContext context: Context): File = File(context.filesDir, "separacoes")

    /** As pastas do SAF, com cada `.sng` lido como uma pasta de mapa. */
    @Provides
    fun folderSource(saf: SafFolderSource, opener: RandomReadOpener): FolderSource = SngFolderSource(saf, opener)

    @Provides
    fun chartImporter(
        source: FolderSource,
        repository: ChartRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): ChartImporter = FolderChartImporter(
        source,
        repository,
        dispatcher,
        FolderChartImporter.MAX_FILE_BYTES,
        System::currentTimeMillis,
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SeparationQueueModule {
    @Binds
    abstract fun separationQueue(impl: RoomSeparationQueue): SeparationQueue
}
