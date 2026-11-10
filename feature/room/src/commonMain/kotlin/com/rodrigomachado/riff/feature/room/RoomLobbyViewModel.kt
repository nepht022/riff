package com.rodrigomachado.riff.feature.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.data.download.ChartDownloadException
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.room.NoConnection
import com.rodrigomachado.riff.core.room.Room
import com.rodrigomachado.riff.core.room.RoomClock
import com.rodrigomachado.riff.core.room.RoomPlayer
import com.rodrigomachado.riff.core.room.RoomRepository
import com.rodrigomachado.riff.core.room.RoomSong
import com.rodrigomachado.riff.core.room.RoomState
import com.rodrigomachado.riff.core.room.SongStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A música da sala neste aparelho. */
sealed interface MySong {
    /** Conferindo na biblioteca. */
    data object Checking : MySong

    data object Has : MySong

    /** Baixando do Encore ([progress] de 0 a 1). */
    data class Downloading(val progress: Float) : MySong

    /** Não tenho: [canDownload] = veio do Encore ("Baixar do Encore"); [failed] = o último download não deu. */
    data class Missing(val canDownload: Boolean, val failed: Boolean = false) : MySong
}

/** Por que o Começar do dono está desligado (o aviso embaixo dele). */
enum class StartHint {
    /** O dono não está pronto: ele joga a rodada (é quem marca o começo). */
    BE_READY,

    /** Menos de 2 prontos. */
    NEED_TWO,
}

sealed interface RoomLobbyUiState {
    data object Loading : RoomLobbyUiState

    /**
     * A sala ao vivo; [mySong] null = nenhuma música escolhida. [canStart] = o Começar habilitado agora (as regras do
     * [Room.canStart] no relógio do servidor); [startPressed] = o Começar foi apertado e o pedido está a caminho.
     */
    data class Ready(
        val room: Room,
        val mySong: MySong? = null,
        val canStart: Boolean = false,
        val startPressed: Boolean = false,
    ) : RoomLobbyUiState {
        /** "Começando…": o Começar foi apertado ou a sala já saiu da escolha (contagem ou partida). */
        val starting: Boolean get() = startPressed || room.state != RoomState.LOBBY

        val me: RoomPlayer? get() = room.players.firstOrNull { it.uid == room.me }

        /** Vivos prontos para tocar (Pronto, com a música e a dificuldade). */
        val readyCount: Int get() = room.players.count { !it.gone && it.isReady() }

        /** O aviso do Começar do dono (null = nenhum, ou não sou o dono). */
        val startHint: StartHint?
            get() = when {
                !room.iAmOwner -> null
                room.song != null && me?.isReady() != true -> StartHint.BE_READY
                readyCount < MIN_READY -> StartHint.NEED_TWO
                else -> null
            }

        /** Começar habilitado sem todos os que têm a música prontos: passou dos 30 s ("com quem está pronto"). */
        val withWhoIsReady: Boolean
            get() = canStart && readyCount < room.players.count { !it.gone && it.song == SongStatus.HAS }
    }

    /** A sala não existe mais (o último saiu, ou ela morreu) ou eu não estou mais nela. */
    data object Ended : RoomLobbyUiState

    /** Sem internet para entrar no Firebase: tenta de novo sozinho. */
    data object NoConnection : RoomLobbyUiState
}

/** Abrir a partida da rodada [round] com o mapa [chartId] na [difficulty] escolhida na sala. */
data class MatchTicket(val chartId: String, val difficulty: Difficulty, val round: Int)

/**
 * A sala [code] ao vivo. Enquanto a tela está aberta (alguém olha o [uiState]), segue a sala pelo streaming e manda o
 * batimento a cada 20 s; com o app em segundo plano, os dois param (e o jogador some para os outros em 60 s). No
 * celular, o Hilt cria com o código da rota.
 *
 * Na M2, a música: a cada música nova o app confere na biblioteca ([songs]) se a tem e grava o próprio estado (a
 * dificuldade fica se a música nova também a tem; o Pronto volta a "não"); sem ela, baixa do Encore quando a música
 * veio de lá. O dono escolhe a música ([chooseSong]) e começa ([start]); quando a sala entra em contagem numa rodada
 * nova com este jogador pronto, o [navigateToMatch] pede para abrir a partida.
 */
@Suppress("TooManyFunctions") // uma função por botão da sala, mais a conferência da música
open class RoomLobbyViewModel(
    private val code: String,
    private val repository: RoomRepository,
    private val songs: RoomSongCheck = RoomSongCheck.NONE,
) : ViewModel() {
    private val leaving = MutableStateFlow(false)
    private val done = MutableStateFlow(false)
    private val latest = MutableStateFlow<Room?>(null)

    /** A conferência da música: o `chartId` conferido e como ela está aqui. */
    private val mine = MutableStateFlow<Pair<String, MySong>?>(null)
    private val pressedStart = MutableStateFlow(false)
    private val ticket = MutableStateFlow<MatchTicket?>(null)
    private var checkedChart: String? = null
    private var songJob: Job? = null
    private var handledRound = -1

    @Volatile
    private var clock: RoomClock? = null
    private var measuring: Job? = null

    /** Saiu da sala: a tela volta. */
    val left: StateFlow<Boolean> = done

    /** A partida a abrir (a tela abre e avisa com [onMatchOpened]). */
    val navigateToMatch: StateFlow<MatchTicket?> = ticket

    val uiState: StateFlow<RoomLobbyUiState> = channelFlow {
        launch { heartbeats() }
        val rooms = repository.observe(code).onEach { room ->
            latest.value = room
            room?.let(::onRoom)
        }
        combine(rooms, mine, pressedStart, ticks()) { room, my, pressed, _ ->
            room?.let { ready(it, my, pressed) } ?: RoomLobbyUiState.Ended
        }
            // Sair me tira da sala (ou a apaga): a tela volta sem mostrar "a sala acabou" no caminho.
            .filterNot { it == RoomLobbyUiState.Ended && leaving.value }
            // Qualquer falha (sem internet, o login recusado, uma resposta estranha) é "sem conexão" e tenta de novo:
            // nenhuma exceção chega ao viewModelScope (fecharia o app). O cancelamento não passa por aqui.
            .retryWhen { _, _ ->
                emit(RoomLobbyUiState.NoConnection)
                delay(RETRY_MS)
                true
            }
            .collect { send(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), RoomLobbyUiState.Loading)

    /** Sai da sala (o dono passa adiante; o último apaga a sala) e então avisa a tela pelo [left]. */
    fun leave() {
        if (leaving.value) return
        leaving.value = true
        viewModelScope.launch {
            repository.leave(code)
            done.value = true
        }
    }

    /** O dono escolheu [chartId] na Biblioteca (modo de escolha), para tocar em [difficulty]. */
    fun chooseSong(chartId: String, difficulty: Difficulty) {
        viewModelScope.launch {
            val song = songs.songOf(chartId) ?: return@launch
            repository.chooseSong(code, song, difficulty)
        }
    }

    /** A minha dificuldade, entre as da música (com a música aqui); o Pronto fica como está. */
    fun chooseDifficulty(d: Difficulty) {
        val room = latest.value ?: return
        val me = room.player(room.me) ?: return
        if (room.song?.difficulties?.contains(d) != true || myStatus(room) != MySong.Has) return
        viewModelScope.launch { repository.setMe(code, d, me.ready, SongStatus.HAS) }
    }

    /** Pronto / Não estou pronto: só com a música aqui e a dificuldade escolhida. */
    fun toggleReady() {
        val room = latest.value ?: return
        val me = room.player(room.me) ?: return
        if (myStatus(room) != MySong.Has || me.difficulty == null || room.state != RoomState.LOBBY) return
        viewModelScope.launch { repository.setMe(code, me.difficulty, !me.ready, SongStatus.HAS) }
    }

    /** Baixar do Encore a música que falta; no fim confere de novo pelo `chartId`. */
    fun download() {
        val song = latest.value?.song ?: return
        val status = myStatus(latest.value)
        if (status !is MySong.Missing || !songs.canDownload(song)) return
        songJob?.cancel()
        songJob = viewModelScope.launch {
            mine.value = song.chartId to MySong.Downloading(0f)
            writeMe(song, SongStatus.DOWNLOADING, keepReady = false, always = true)
            val has = try {
                songs.download(song) { mine.value = song.chartId to MySong.Downloading(it) }
            } catch (_: ChartDownloadException) {
                false
            }
            mine.value = song.chartId to if (has) MySong.Has else MySong.Missing(canDownload = true, failed = true)
            // A sala lida pode ainda não ter o "baixando": grava sempre.
            writeMe(song, if (has) SongStatus.HAS else SongStatus.MISSING, keepReady = false, always = true)
        }
    }

    /** O Começar (o dono): a rodada seguinte, em contagem. O repositório confere as regras de novo. */
    fun start() {
        if (pressedStart.value) return
        pressedStart.value = true
        viewModelScope.launch {
            try {
                repository.startRound(code)
            } finally {
                pressedStart.value = false
            }
        }
    }

    /** A tela abriu a partida do [navigateToMatch]. */
    fun onMatchOpened() {
        ticket.value = null
    }

    /** Cada sala lida: confere a música nova, mede o relógio (o dono, com música) e abre a partida da rodada nova. */
    private fun onRoom(room: Room) {
        val chart = room.song?.chartId
        if (chart != checkedChart) {
            val first = checkedChart == null
            checkedChart = chart
            songJob?.cancel()
            room.song?.let { song ->
                mine.value = song.chartId to MySong.Checking
                songJob = viewModelScope.launch { check(song, keepReady = first) }
            }
        }
        // O relógio só serve ao Começar do dono (os 30 s): mede quando há música para começar.
        val needsClock = room.iAmOwner && room.song != null
        if (needsClock && clock == null && measuring == null) {
            measuring = viewModelScope.launch { measure() }
        }
        if (room.state == RoomState.COUNTDOWN && room.round > handledRound) {
            handledRound = room.round
            val difficulty = room.playersInRound().firstOrNull { it.uid == room.me }?.difficulty
            val song = room.song
            if (difficulty != null && song != null) ticket.value = MatchTicket(song.chartId, difficulty, room.round)
        }
    }

    /**
     * Tenho a música [song]? Grava "tem"/"sem". O Pronto só fica na primeira conferência ([keepReady]: a sala aberta
     * com a música de antes); numa música nova, volta a "não".
     */
    private suspend fun check(song: RoomSong, keepReady: Boolean) {
        val has = songs.has(song.chartId)
        mine.value = song.chartId to if (has) MySong.Has else MySong.Missing(songs.canDownload(song))
        writeMe(song, if (has) SongStatus.HAS else SongStatus.MISSING, keepReady)
    }

    /** O meu estado para [song], só se mudou ou [always] (a dificuldade fica se a música a tem). */
    private suspend fun writeMe(song: RoomSong, status: SongStatus, keepReady: Boolean, always: Boolean = false) {
        val room = latest.value ?: return
        val me = room.player(room.me) ?: return
        val difficulty = me.difficulty?.takeIf { it in song.difficulties }
        val ready = keepReady && me.ready && status == SongStatus.HAS && difficulty != null
        val changed = me.song != status || me.ready != ready || me.difficulty != difficulty
        if (always || changed) {
            repository.setMe(code, difficulty, ready, status)
        }
    }

    private fun ready(room: Room, my: Pair<String, MySong>?, pressed: Boolean): RoomLobbyUiState.Ready {
        // Sem o relógio medido, o "agora" não passa do primeiro Pronto: só o Começar com todos prontos vale.
        val now = clock?.serverNow() ?: room.firstReadyAt ?: 0L
        return RoomLobbyUiState.Ready(
            room = room,
            mySong = myStatus(room, my),
            canStart = room.iAmOwner && !pressed && room.canStart(now),
            startPressed = pressed,
        )
    }

    private fun myStatus(room: Room?, my: Pair<String, MySong>? = mine.value): MySong? {
        val song = room?.song ?: return null
        return my?.takeIf { it.first == song.chartId }?.second ?: MySong.Checking
    }

    private suspend fun measure() {
        while (clock == null) {
            try {
                clock = repository.measureClock(code)
            } catch (_: NoConnection) {
                delay(RETRY_MS)
            }
        }
    }

    /** O relógio da tela: os 30 s do "Começar com quem está pronto" passam sem nada mudar no banco. */
    private fun ticks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MS)
        }
    }

    private suspend fun heartbeats() {
        while (true) {
            if (!leaving.value) repository.heartbeat(code)
            delay(HEARTBEAT_MS)
        }
    }

    private companion object {
        const val HEARTBEAT_MS = 20_000L
        const val RETRY_MS = 5_000L
        const val TICK_MS = 1_000L

        // Girar a tela não para o streaming; o app em segundo plano, sim.
        const val STOP_MS = 5_000L
    }
}

private const val MIN_READY = 2

private fun Room.player(uid: String): RoomPlayer? = players.firstOrNull { it.uid == uid }

/** Pronto para tocar: marcou Pronto, tem a música e escolheu a dificuldade (como o `RoomRound`). */
private fun RoomPlayer.isReady() = ready && song == SongStatus.HAS && difficulty != null
