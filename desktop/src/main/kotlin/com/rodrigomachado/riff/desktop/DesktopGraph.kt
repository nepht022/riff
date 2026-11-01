package com.rodrigomachado.riff.desktop

import com.rodrigomachado.riff.core.audio.AudioSinkFactory
import com.rodrigomachado.riff.core.audio.ClipMenuSounds
import com.rodrigomachado.riff.core.audio.ClipSfxPlayer
import com.rodrigomachado.riff.core.audio.DesktopPlayers
import com.rodrigomachado.riff.core.audio.MenuSoundPlayer
import com.rodrigomachado.riff.core.audio.SfxPlayerFactory
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.NoAiSeparations
import com.rodrigomachado.riff.core.data.NoSeparationScheduler
import com.rodrigomachado.riff.core.data.PreferencesLibraryFolderStore
import com.rodrigomachado.riff.core.data.PreferencesSettingsRepository
import com.rodrigomachado.riff.core.data.RoomChartOffsetRepository
import com.rodrigomachado.riff.core.data.RoomChartRepository
import com.rodrigomachado.riff.core.data.RoomScoreRepository
import com.rodrigomachado.riff.core.data.ScoreRepository
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.folder.FileFolderAccess
import com.rodrigomachado.riff.core.data.folder.FileFolderSource
import com.rodrigomachado.riff.core.data.folder.FileFolderWriter
import com.rodrigomachado.riff.core.data.folder.FileRandomReadOpener
import com.rodrigomachado.riff.core.data.folder.SngFolderSource
import com.rodrigomachado.riff.core.data.game.FolderChartArtLoader
import com.rodrigomachado.riff.core.data.game.FolderChartAudioProbe
import com.rodrigomachado.riff.core.data.game.FolderChartTrackLoader
import com.rodrigomachado.riff.core.data.importer.FolderChartImporter
import com.rodrigomachado.riff.core.data.library.FolderChartDeleter
import com.rodrigomachado.riff.core.data.ranking.NoRanking
import com.rodrigomachado.riff.core.database.riffDatabase
import com.rodrigomachado.riff.core.datastore.RiffPreferences
import com.rodrigomachado.riff.core.datastore.riffDataStore
import com.rodrigomachado.riff.core.game.TimeSource
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.feature.gameplay.DecodedChartArtImages
import com.rodrigomachado.riff.feature.gameplay.GameplayViewModel
import com.rodrigomachado.riff.feature.library.LibraryViewModel
import com.rodrigomachado.riff.feature.results.ResultsViewModel
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
    /** A saída da música da partida (nos testes, uma que só anda no ritmo do relógio: o CI não tem placa de som). */
    audioOut: AudioSinkFactory = DesktopPlayers.speakers,
    /** Os sons de acerto e de erro da partida. */
    private val sfx: SfxPlayerFactory = SfxPlayerFactory { ClipSfxPlayer() },
) : AutoCloseable {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val database = riffDatabase(File(dataDir, "riff.db"))
    private val preferences = RiffPreferences(riffDataStore(File(dataDir, "riff_settings.preferences_pb"), scope))
    private val io = Dispatchers.IO

    internal val charts: ChartRepository = RoomChartRepository(database.chartDao())
    internal val scores: ScoreRepository = RoomScoreRepository(database.scoreDao())
    private val randomRead = FileRandomReadOpener()
    private val folderSource = SngFolderSource(FileFolderSource(), randomRead)
    val settings: SettingsRepository = PreferencesSettingsRepository(preferences)
    val languages = DesktopAppLanguageStore(File(dataDir, "language.txt"))
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

    // A partida: a música (um arquivo ou faixas separadas) pelo FFmpeg e o relógio monotônico do PC.
    private val songPlayers = DesktopPlayers.songPlayers(randomRead, audioOut)
    private val stemPlayers = DesktopPlayers.stemPlayers(randomRead, audioOut)
    private val trackLoader = FolderChartTrackLoader(folderSource, charts, NoAiSeparations, io)
    private val chartOffsets = RoomChartOffsetRepository(database.chartOffsetDao())
    private val chartArt = DecodedChartArtImages(FolderChartArtLoader(folderSource, charts, io))
    private val clock = TimeSource { System.nanoTime() / NANOS_PER_MS }

    fun gameplayViewModel(chartId: String, difficulty: Difficulty) = GameplayViewModel(
        chartId = chartId,
        difficulty = difficulty,
        loader = trackLoader,
        settingsRepository = settings,
        scores = scores,
        chartOffsets = chartOffsets,
        songPlayers = songPlayers,
        stemPlayers = stemPlayers,
        sfxPlayers = sfx,
        timeSource = clock,
        interruptionsFactory = DesktopPlayers.interruptions,
        artImages = chartArt,
        // O ranking no PC é da etapa 4: nada enviado, e o Resultado sem o cartão.
        ranking = NoRanking,
    )

    fun resultsViewModel(scoreId: Long) = ResultsViewModel(scoreId, scores, charts, NoRanking)

    override fun close() {
        database.close()
        scope.cancel()
    }
}

private const val NANOS_PER_MS = 1_000_000L
