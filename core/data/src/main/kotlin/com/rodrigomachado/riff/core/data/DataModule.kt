package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.data.folder.FolderSource
import com.rodrigomachado.riff.core.data.folder.SafFolderAccess
import com.rodrigomachado.riff.core.data.folder.SafFolderSource
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
    abstract fun folderSource(impl: SafFolderSource): FolderSource

    @Binds
    abstract fun folderAccess(impl: SafFolderAccess): FolderAccess
}

@Module
@InstallIn(SingletonComponent::class)
object DataProvidesModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

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
