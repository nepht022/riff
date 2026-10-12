package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
import com.rodrigomachado.riff.core.data.folder.ContentRandomReadOpener
import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.data.folder.FolderSource
import com.rodrigomachado.riff.core.data.folder.FolderWriter
import com.rodrigomachado.riff.core.data.folder.SafFolderAccess
import com.rodrigomachado.riff.core.data.folder.SafFolderSource
import com.rodrigomachado.riff.core.data.folder.SafFolderWriter
import com.rodrigomachado.riff.core.data.folder.SngFolderSource
import com.rodrigomachado.riff.core.data.game.ChartTrackLoader
import com.rodrigomachado.riff.core.data.game.FolderChartTrackLoader
import com.rodrigomachado.riff.core.data.importer.ChartImporter
import com.rodrigomachado.riff.core.data.importer.FolderChartImporter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
}

/** Acesso à pasta da biblioteca pelo SAF: permissão, leitura por posição (`.sng`) e gravação (downloads). */
@Module
@InstallIn(SingletonComponent::class)
abstract class FolderBindsModule {
    @Binds
    abstract fun folderAccess(impl: SafFolderAccess): FolderAccess

    @Binds
    abstract fun randomReadOpener(impl: ContentRandomReadOpener): RandomReadOpener

    @Binds
    abstract fun folderWriter(impl: SafFolderWriter): FolderWriter
}

@Module
@InstallIn(SingletonComponent::class)
object DataProvidesModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

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
