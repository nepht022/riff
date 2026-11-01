package com.rodrigomachado.riff.desktop

import com.rodrigomachado.riff.core.audio.ClipMenuSounds
import com.rodrigomachado.riff.core.audio.MenuSoundPlayer
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.NoAiSeparations
import com.rodrigomachado.riff.core.data.NoSeparationScheduler
import com.rodrigomachado.riff.core.data.PreferencesLibraryFolderStore
import com.rodrigomachado.riff.core.data.PreferencesSettingsRepository
import com.rodrigomachado.riff.core.data.RoomChartRepository
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.folder.FileFolderAccess
import com.rodrigomachado.riff.core.data.folder.FileFolderSource
import com.rodrigomachado.riff.core.data.folder.FileFolderWriter
import com.rodrigomachado.riff.core.data.folder.FileRandomReadOpener
import com.rodrigomachado.riff.core.data.folder.SngFolderSource
import com.rodrigomachado.riff.core.data.game.FolderChartAudioProbe
import com.rodrigomachado.riff.core.data.importer.FolderChartImporter
import com.rodrigomachado.riff.core.data.library.FolderChartDeleter
import com.rodrigomachado.riff.core.database.riffDatabase
import com.rodrigomachado.riff.core.datastore.RiffPreferences
import com.rodrigomachado.riff.core.datastore.riffDataStore
import com.rodrigomachado.riff.feature.library.LibraryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File

/**
 * As dependências do Riff no PC, montadas à mão (no celular quem monta é o Hilt). Banco e preferências ficam em
 * [dataDir]; feche ao sair.
 */
class DesktopGraph(
    dataDir: File,
    /** Os sons dos botões (nos testes, um que só anota). */
    val menuSounds: MenuSoundPlayer = ClipMenuSounds(),
) : AutoCloseable {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val database = riffDatabase(File(dataDir, "riff.db"))
    private val preferences = RiffPreferences(riffDataStore(File(dataDir, "riff_settings.preferences_pb"), scope))
    private val io = Dispatchers.IO

    private val charts = RoomChartRepository(database.chartDao())
    private val folderSource = SngFolderSource(FileFolderSource(), FileRandomReadOpener())
    val settings: SettingsRepository = PreferencesSettingsRepository(preferences)
    val libraryFolderStore: LibraryFolderStore = PreferencesLibraryFolderStore(preferences)
    val chartImporter = FolderChartImporter(folderSource, charts, io, FolderChartImporter.MAX_FILE_BYTES) {
        System.currentTimeMillis()
    }

    fun libraryViewModel() = LibraryViewModel(
        chartRepository = charts,
        folderStore = libraryFolderStore,
        importer = chartImporter,
        folderAccess = FileFolderAccess(),
        separations = NoAiSeparations,
        scheduler = NoSeparationScheduler,
        probe = FolderChartAudioProbe(folderSource, charts, io),
        settings = settings,
        deleter = FolderChartDeleter(charts, FileFolderWriter(), libraryFolderStore, NoAiSeparations, io),
    )

    override fun close() {
        database.close()
        scope.cancel()
    }
}
