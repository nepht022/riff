package com.rodrigomachado.riff.desktop

import androidx.lifecycle.SavedStateHandle
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
import com.rodrigomachado.riff.core.data.RoomEncoreDownloads
import com.rodrigomachado.riff.core.data.RoomScoreRepository
import com.rodrigomachado.riff.core.data.ScoreRepository
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.download.EncoreChartDownloader
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
import com.rodrigomachado.riff.core.encore.EncoreClient
import com.rodrigomachado.riff.core.encore.HttpTransport
import com.rodrigomachado.riff.core.encore.UrlConnectionTransport
import com.rodrigomachado.riff.core.game.TimeSource
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.desktop.pad.GlfwGamepad
import com.rodrigomachado.riff.desktop.pad.GlfwPadSource
import com.rodrigomachado.riff.desktop.pad.PadSource
import com.rodrigomachado.riff.feature.calibration.AudioClickTrackPlayer
import com.rodrigomachado.riff.feature.calibration.CalibrationViewModel
import com.rodrigomachado.riff.feature.calibration.ClickTrackPlayer
import com.rodrigomachado.riff.feature.gameplay.DecodedChartArtImages
import com.rodrigomachado.riff.feature.gameplay.GameplayViewModel
import com.rodrigomachado.riff.feature.library.LibraryViewModel
import com.rodrigomachado.riff.feature.licenses.DesktopLicenseSource
import com.rodrigomachado.riff.feature.licenses.LicensesViewModel
import com.rodrigomachado.riff.feature.report.AppsScriptReportSender
import com.rodrigomachado.riff.feature.report.DesktopDeviceInfo
import com.rodrigomachado.riff.feature.report.JsonPost
import com.rodrigomachado.riff.feature.report.ReportConfig
import com.rodrigomachado.riff.feature.report.ReportCooldown
import com.rodrigomachado.riff.feature.report.ReportViewModel
import com.rodrigomachado.riff.feature.report.UrlConnectionJsonPost
import com.rodrigomachado.riff.feature.results.ResultsViewModel
import com.rodrigomachado.riff.feature.search.SearchViewModel
import com.rodrigomachado.riff.feature.settings.SettingsViewModel
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
    /** Os sons de acerto e de erro da partida (e a prévia do volume do erro nas Configurações). */
    private val sfx: SfxPlayerFactory = SfxPlayerFactory { ClipSfxPlayer() },
    /** O metrônomo da calibração (nos testes, um que devolve cliques montados a partir das teclas). */
    private val clickTrack: ClickTrackPlayer = AudioClickTrackPlayer(audioOut),
    /** O controle (nos testes, uma fonte sem controle: o CI não tem tela para o GLFW). */
    padSource: PadSource = GlfwPadSource(),
    /** A conversa com o Encore (nos testes, um Encore falso: nunca a rede). */
    encoreTransport: HttpTransport = UrlConnectionTransport(),
    /** O script do Reportar (o mesmo do celular, do local.properties; vazio: o envio não está configurado). */
    reportUrl: String = ReportConfig.REPORT_URL,
    /** O POST dos relatos (nos testes, um script falso: nunca um relato de verdade). */
    reportTransport: JsonPost = UrlConnectionJsonPost,
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

    // A pesquisa no Encore: baixa para "Baixados pelo Riff" na pasta da biblioteca e importa o pacote na hora.
    private val encore = EncoreClient(encoreTransport, io)
    private val encoreDownloads = RoomEncoreDownloads(database.encoreChartDao())
    private val downloader = EncoreChartDownloader(
        encore = encore,
        folders = libraryFolderStore,
        access = FileFolderAccess(),
        writer = FileFolderWriter(),
        importer = chartImporter,
        downloads = encoreDownloads,
        dispatcher = io,
        opener = randomRead,
    )

    /** A pesquisa aberta pela Biblioteca ([query] vazia) ou já com uma busca. */
    fun searchViewModel(query: String?) = SearchViewModel(
        initialQuery = query.orEmpty(),
        encore = encore,
        downloader = downloader,
        downloads = encoreDownloads,
        folderAccess = FileFolderAccess(),
        folders = libraryFolderStore,
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

    // Sem a IA (a aba some) e sem o ranking (a linha do nome some) no PC: os dois são da etapa 4.
    fun settingsViewModel() = SettingsViewModel(settings, NoAiSeparations, languages, NoRanking, sfx)

    fun calibrationViewModel() = CalibrationViewModel(settings, clickTrack)

    // O Reportar: o mesmo script do celular; a espera entre relatos vale para o app inteiro (um só relógio).
    private val reportSender = AppsScriptReportSender(reportUrl, reportTransport)
    private val reportCooldown = ReportCooldown()
    private val deviceInfo = DesktopDeviceInfo(language = { languages.current.tag })

    /** O formulário do Reportar; [saved] guarda o que foi escrito enquanto a tela existir. */
    fun reportViewModel(saved: SavedStateHandle = SavedStateHandle()) =
        ReportViewModel(saved, reportSender, reportCooldown, deviceInfo)

    /** Licenças e privacidade: a lista das bibliotecas gerada no build do app do PC (aboutlibraries.json). */
    fun licensesViewModel() = LicensesViewModel(
        DesktopLicenseSource { DesktopGraph::class.java.getResource(LIBRARIES_JSON)?.readText() },
    )

    /** O controle do PC, lido na própria thread desde a abertura do jogo. */
    val gamepad = GlfwGamepad(padSource)

    override fun close() {
        gamepad.close()
        database.close()
        scope.cancel()
    }
}

private const val NANOS_PER_MS = 1_000_000L
private const val LIBRARIES_JSON = "/aboutlibraries.json"
