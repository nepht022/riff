package com.rodrigomachado.riff.desktop

import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.audio.AudioSinkFactory
import com.rodrigomachado.riff.core.audio.ClipMenuSounds
import com.rodrigomachado.riff.core.audio.ClipSfxPlayer
import com.rodrigomachado.riff.core.audio.DesktopPlayers
import com.rodrigomachado.riff.core.audio.MenuSoundPlayer
import com.rodrigomachado.riff.core.audio.SfxPlayerFactory
import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.AiSeparationState
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.PreferencesLibraryFolderStore
import com.rodrigomachado.riff.core.data.PreferencesSettingsRepository
import com.rodrigomachado.riff.core.data.RankingConfig
import com.rodrigomachado.riff.core.data.RoomAiSeparationRepository
import com.rodrigomachado.riff.core.data.RoomChartOffsetRepository
import com.rodrigomachado.riff.core.data.RoomChartRepository
import com.rodrigomachado.riff.core.data.RoomEncoreDownloads
import com.rodrigomachado.riff.core.data.RoomScoreRepository
import com.rodrigomachado.riff.core.data.RoomSeparationQueue
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
import com.rodrigomachado.riff.core.data.ranking.AppsScriptRankingRepository
import com.rodrigomachado.riff.core.data.ranking.DataStorePlayerStore
import com.rodrigomachado.riff.core.data.ranking.RankingApi
import com.rodrigomachado.riff.core.data.ranking.RankingRepository
import com.rodrigomachado.riff.core.data.ranking.RankingTransport
import com.rodrigomachado.riff.core.data.ranking.RoomScoreHistory
import com.rodrigomachado.riff.core.data.ranking.UrlConnectionRankingTransport
import com.rodrigomachado.riff.core.database.riffDatabase
import com.rodrigomachado.riff.core.datastore.RiffPreferences
import com.rodrigomachado.riff.core.datastore.riffDataStore
import com.rodrigomachado.riff.core.encore.EncoreClient
import com.rodrigomachado.riff.core.encore.HttpTransport
import com.rodrigomachado.riff.core.encore.UrlConnectionTransport
import com.rodrigomachado.riff.core.game.TimeSource
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.RankingTable
import com.rodrigomachado.riff.core.room.AnonymousAuth
import com.rodrigomachado.riff.core.room.DataStoreRoomIdentityStore
import com.rodrigomachado.riff.core.room.FirebaseDb
import com.rodrigomachado.riff.core.room.RoomConfig
import com.rodrigomachado.riff.core.room.RoomDevice
import com.rodrigomachado.riff.core.room.RoomHttp
import com.rodrigomachado.riff.core.room.RoomPresence
import com.rodrigomachado.riff.core.room.RoomRepository
import com.rodrigomachado.riff.core.room.RoomSwitch
import com.rodrigomachado.riff.core.room.UrlConnectionRoomHttp
import com.rodrigomachado.riff.core.separation.DemucsModelFactory
import com.rodrigomachado.riff.core.separation.DesktopSeparation
import com.rodrigomachado.riff.core.separation.DesktopSeparationScheduler
import com.rodrigomachado.riff.core.separation.SeparationProgress
import com.rodrigomachado.riff.core.ui.component.SeparationControls
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
import com.rodrigomachado.riff.feature.ranking.PlayerNameViewModel
import com.rodrigomachado.riff.feature.ranking.RankingBoardViewModel
import com.rodrigomachado.riff.feature.ranking.RankingViewModel
import com.rodrigomachado.riff.feature.report.AppsScriptReportSender
import com.rodrigomachado.riff.feature.report.DesktopDeviceInfo
import com.rodrigomachado.riff.feature.report.JsonPost
import com.rodrigomachado.riff.feature.report.ReportConfig
import com.rodrigomachado.riff.feature.report.ReportCooldown
import com.rodrigomachado.riff.feature.report.ReportViewModel
import com.rodrigomachado.riff.feature.report.UrlConnectionJsonPost
import com.rodrigomachado.riff.feature.results.ResultsViewModel
import com.rodrigomachado.riff.feature.room.RoomEntryViewModel
import com.rodrigomachado.riff.feature.room.RoomJoinViewModel
import com.rodrigomachado.riff.feature.room.RoomLiveBoard
import com.rodrigomachado.riff.feature.room.RoomLobbyViewModel
import com.rodrigomachado.riff.feature.room.RoomMatchStart
import com.rodrigomachado.riff.feature.room.RoomSongCheck
import com.rodrigomachado.riff.feature.search.SearchViewModel
import com.rodrigomachado.riff.feature.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.concurrent.thread
import com.rodrigomachado.riff.core.separation.DesktopDeviceInfo as SeparationDeviceInfo

/**
 * As dependências do Riff no PC, montadas à mão (no celular quem monta é o Hilt). Banco e preferências ficam em
 * [dataDir]; feche ao sair. Um só por [dataDir]: com outro aberto, falha com [RiffAlreadyOpenException] antes de abrir
 * o banco e a fila.
 */
@Suppress("TooManyFunctions") // uma fábrica por tela, como os módulos do Hilt no celular
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
    /** O modelo da guitarra por IA (nos testes, um falso e rápido: nunca o de 130 MB). */
    separationModels: DemucsModelFactory = DesktopSeparation.models(),
    /** A prioridade da thread da separação: baixa (nos testes, a normal: com a suíte ocupando o PC, a baixa para). */
    separationThreadPriority: Int = Thread.MIN_PRIORITY,
    /** O script do ranking (o mesmo do celular, do local.properties; vazio: o ranking fica desligado, como lá). */
    rankingUrl: String = RankingConfig.RANKING_URL,
    /**
     * O POST do ranking (nos testes, um script falso: nunca o de verdade). O padrão é o HTTP do Java fora da thread
     * da janela, que segue o redirecionamento do Apps Script como o do Reportar.
     */
    rankingTransport: RankingTransport = UrlConnectionRankingTransport,
    /** O Firebase da Sala (o mesmo do celular, do local.properties; vazio: a Sala fica "em breve", como lá). */
    roomApiKey: String = RoomConfig.API_KEY,
    roomDbUrl: String = RoomConfig.DB_URL,
    /** O HTTP da Sala (nos testes, o Firebase falso: nunca o de verdade). */
    roomHttp: RoomHttp = UrlConnectionRoomHttp,
) : AutoCloseable {
    // Primeiro a trava: o segundo Riff não chega a abrir o banco nem a apagar os temporários da separação do primeiro.
    private val instanceLock = InstanceLock.tryAcquire(dataDir) ?: throw RiffAlreadyOpenException(dataDir)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val database = riffDatabase(File(dataDir, "riff.db"))
    private val preferences = RiffPreferences(riffDataStore(File(dataDir, "riff_settings.preferences_pb"), scope))
    private val io = Dispatchers.IO

    internal val charts: ChartRepository = RoomChartRepository(database.chartDao())
    internal val scores: ScoreRepository = RoomScoreRepository(database.scoreDao())
    private val randomRead = FileRandomReadOpener()
    private val folderSource = SngFolderSource(FileFolderSource(), randomRead)
    val settings: SettingsRepository = PreferencesSettingsRepository(preferences)
    val languages = DesktopAppLanguageStore(File(dataDir, LANGUAGE_FILE))
    val libraryFolderStore: LibraryFolderStore = PreferencesLibraryFolderStore(preferences)
    val chartImporter = FolderChartImporter(folderSource, charts, io, FolderChartImporter.MAX_FILE_BYTES) {
        System.currentTimeMillis()
    }
    private val audioProbe = FolderChartAudioProbe(folderSource, charts, io)

    // A guitarra separada por IA: `separacoes` na pasta de dados (uma pasta por música, como no celular), a fila no
    // banco e o rascunho (a música decodificada) ao lado. A fila roda só com o Riff aberto e continua ao abrir.
    private val separationsDir = File(dataDir, "separacoes").apply { mkdirs() }
    private val separationScratch = File(dataDir, "separacao-temp").apply { mkdirs() }
    internal val separations: AiSeparationRepository =
        RoomAiSeparationRepository(database.aiSeparationDao(), separationsDir, io)
    internal val separationScheduler: DesktopSeparationScheduler = run {
        val progress = SeparationProgress()
        val device = SeparationDeviceInfo(separationsDir)
        val runner = DesktopSeparation.runner(
            repo = separations,
            probe = audioProbe,
            randomRead = randomRead,
            progress = progress,
            device = device,
            scratch = separationScratch,
            models = separationModels,
        )
        DesktopSeparationScheduler(
            separationQueue = RoomSeparationQueue(database.separationQueueDao()),
            progress = progress,
            separate = runner::run,
            isSeparated = { separations.stateOf(it) == AiSeparationState.SEPARATED },
            lowMemoryDevice = device.lowMemory,
            separationsDir = separationsDir,
            scratch = separationScratch,
            pruneOrphans = separations::pruneOrphans,
            threadPriority = separationThreadPriority,
        )
    }

    /** A palheta do topo das telas e o painel dela (a fila, o progresso, cancelar). */
    val separationControls: SeparationControls = object : SeparationControls {
        override val status = separationScheduler.status
        override val queue = separationScheduler.queue
        override val waitingForCharger = separationScheduler.waitingForCharger

        override fun cancel(chartId: String) = separationScheduler.cancelCurrent(chartId)

        override fun remove(chartId: String) = separationScheduler.remove(chartId)

        override fun cancelAll() = separationScheduler.cancelAll()

        override fun dismissFailure() = separationScheduler.dismissFailure()
    }

    fun libraryViewModel() = LibraryViewModel(
        chartRepository = charts,
        folderStore = libraryFolderStore,
        importer = chartImporter,
        folderAccess = FileFolderAccess(),
        separations = separations,
        scheduler = separationScheduler,
        probe = audioProbe,
        settings = settings,
        deleter = FolderChartDeleter(charts, FileFolderWriter(), libraryFolderStore, separations, io),
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
    internal val trackLoader = FolderChartTrackLoader(folderSource, charts, separations, io)
    private val chartOffsets = RoomChartOffsetRepository(database.chartOffsetDao())
    private val chartArt = DecodedChartArtImages(FolderChartArtLoader(folderSource, charts, io))
    private val clock = TimeSource { System.nanoTime() / NANOS_PER_MS }

    // O ranking, como no celular: o nome e a chave do PC nas preferências, o histórico do banco do PC e a fila no
    // banco; o envio continua no escopo do app depois de a tela sair. A tabela de cada partida vem do modo dela.
    internal val ranking: RankingRepository = AppsScriptRankingRepository(
        RankingApi(rankingUrl, rankingTransport),
        DataStorePlayerStore(preferences),
        RoomScoreHistory(database.scoreDao()),
        database.rankingPendingDao(),
        charts,
        scope,
    )

    init {
        // O que ficou na fila (sem internet, ou sem nome) sai ao abrir o Riff, como no celular.
        scope.launch { ranking.flush() }
    }

    /**
     * A partida; com [roomCode], a partida de sala da rodada [round]: o começo combinado pelo [RoomMatchStart], no
     * relógio da partida (o mesmo da `GameSession`), sem pausa, e o placar ao vivo pelo [RoomLiveBoard].
     */
    fun gameplayViewModel(chartId: String, difficulty: Difficulty, roomCode: String? = null, round: Int = 0) =
        GameplayViewModel(
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
            ranking = ranking,
            // No PC, a tabela do modo da partida ("Tocar com palheta" de quando ela começou).
            rankingTable = { RankingTable.pc(it.controls.strum) },
            matchStart = roomCode?.let { RoomMatchStart(it, round, room, clock::nowMs) },
            // O placar ao vivo lê a sala que a presença já observa (sem outro streaming) e envia fora da tela.
            liveBoard = roomCode?.let { RoomLiveBoard(it, round, room, roomPresence.room) },
        )

    fun resultsViewModel(scoreId: Long) = ResultsViewModel(scoreId, scores, charts, ranking)

    fun settingsViewModel() = SettingsViewModel(settings, separations, languages, ranking, sfx)

    /** O nome do ranking (o pedido da primeira abertura e a troca pelas Configurações, com o vínculo por código). */
    fun playerNameViewModel() = PlayerNameViewModel(ranking)

    /** As músicas do ranking, abertas na [table] (a do modo atual). */
    fun rankingViewModel(table: RankingTable) = RankingViewModel(ranking, table)

    /** O placar de um mapa, aberto na [difficulty] (nome do enum, ou nula) e na [table]. */
    fun rankingBoardViewModel(chartId: String, title: String, difficulty: String?, table: RankingTable) =
        RankingBoardViewModel(chartId, title, difficulty, ranking, table)

    fun calibrationViewModel() = CalibrationViewModel(settings, clickTrack)

    /** A Sala ligada: só com o Firebase configurado; sem ele, o quadro da tela inicial fica "em breve". */
    val roomOn: Boolean = RoomSwitch.isOn(roomApiKey, roomDbUrl)

    // A Sala, como no celular, com o aparelho PC: um só repositório (o login anônimo vale para todas as telas), a
    // identidade nas preferências do PC e o nome do jogador, o do ranking.
    private val room: RoomRepository = run {
        val clock = System::currentTimeMillis
        val auth = AnonymousAuth(roomHttp, DataStoreRoomIdentityStore(preferences), roomApiKey, clock)
        RoomRepository(FirebaseDb(roomHttp, auth, roomDbUrl), auth, RoomDevice.PC, clock)
    }

    // A presença na sala (o batimento em qualquer tela: a partida, o Resultado, a Biblioteca de escolha) enquanto a
    // janela existe; sair da sala (ou fechar a janela, que sai) para o batimento.
    private val roomPresence = RoomPresence(room, scope)
    private val roomPlayerName = ranking.player.map { it.name }

    fun roomEntryViewModel() = RoomEntryViewModel(room, roomPlayerName)

    fun roomJoinViewModel() = RoomJoinViewModel(room, roomPlayerName)

    /** A sala: a música é conferida na biblioteca do PC e baixada do Encore pelo download da pesquisa. */
    fun roomLobbyViewModel(code: String) =
        RoomLobbyViewModel(code, room, RoomSongCheck.of(charts, encoreDownloads, downloader))

    /**
     * Fechar a janela com a sala aberta sai dela (passa o dono adiante, ou apaga a sala sendo o último), para não
     * deixar um jogador fantasma segurando a vaga. Numa thread de fundo, esperando no máximo ~2 s: o HTTP do Java
     * não se cancela no meio, e o Firebase sem responder não pode segurar o fechamento (sem a saída, o jogador some
     * para os outros em 60 s e o dono o tira em 5 min).
     */
    private fun leaveOpenRoom() {
        val code = room.joined ?: return
        thread(isDaemon = true, name = "riff-sair-da-sala") { runBlocking { room.leave(code) } }
            .join(ROOM_LEAVE_WAIT_MS)
    }

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

    /**
     * Fecha o Riff: sai da sala aberta (ver [leaveOpenRoom]); a separação da vez é cancelada (volta à fila, nada pela
     * metade) antes de o banco fechar; a trava sai por último (um Riff aberto nesse meio-tempo é recusado).
     */
    override fun close() {
        try {
            leaveOpenRoom()
            separationScheduler.close()
            gamepad.close()
            database.close()
            scope.cancel()
        } finally {
            instanceLock.close()
        }
    }
}

private const val NANOS_PER_MS = 1_000_000L
private const val ROOM_LEAVE_WAIT_MS = 2_000L
private const val LIBRARIES_JSON = "/aboutlibraries.json"

/** Outro Riff já está aberto com a mesma pasta de dados. */
class RiffAlreadyOpenException(dataDir: File) : IllegalStateException("o Riff já está aberto com $dataDir")
