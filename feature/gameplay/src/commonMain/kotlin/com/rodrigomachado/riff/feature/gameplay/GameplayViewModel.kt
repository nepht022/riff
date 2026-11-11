package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.audio.PlaybackInterruptions
import com.rodrigomachado.riff.core.audio.PlaybackInterruptionsFactory
import com.rodrigomachado.riff.core.audio.SfxPlayer
import com.rodrigomachado.riff.core.audio.SfxPlayerFactory
import com.rodrigomachado.riff.core.audio.SongPlayer
import com.rodrigomachado.riff.core.audio.SongPlayerFactory
import com.rodrigomachado.riff.core.audio.StemSongPlayerFactory
import com.rodrigomachado.riff.core.data.ChartOffsetRepository
import com.rodrigomachado.riff.core.data.MAX_SONG_OFFSET_MS
import com.rodrigomachado.riff.core.data.ScoreRepository
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.game.ChartAudio
import com.rodrigomachado.riff.core.data.game.ChartNotFoundException
import com.rodrigomachado.riff.core.data.game.ChartTrackLoader
import com.rodrigomachado.riff.core.data.ranking.RankingRepository
import com.rodrigomachado.riff.core.game.GameEngine
import com.rodrigomachado.riff.core.game.TimeSource
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.model.RankingTable
import com.rodrigomachado.riff.core.model.ScoreRecord
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class UnavailableReason {
    NO_AUDIO,
    CHART_MISSING,
    CHART_UNREADABLE,
    FOLDER_ACCESS_LOST,
    AUDIO_FAILED,
    UNEXPECTED,
}

sealed interface GameplayUiState {
    data object Loading : GameplayUiState

    data class Ready(
        val title: String,
        val artist: String,
        val difficulty: Difficulty,
        val session: GameSession,
        val settings: Settings,
        val isPaused: Boolean = false,
        val withoutSeparateGuitar: Boolean = false,
        val songOffsetMs: Int = 0,
        /** Imagem do mapa atrás da pista (com a opção ligada e se o mapa tiver uma). */
        val art: ImageBitmap? = null,
        /** Pausada porque o Android negou o áudio ao continuar (no meio de uma ligação). */
        val audioBusy: Boolean = false,
        /** Partida de sala: sem pausa (nem o botão, nem o Voltar, nem as interrupções), e Sair pergunta antes. */
        val inRoom: Boolean = false,
        /** Partida de sala montada e parada, esperando o começo combinado com os outros. */
        val waitingForStart: Boolean = false,
        /** O instante do começo (relógio da partida, ver [MatchStart]): a contagem 3-2-1 até ele; null = sem. */
        val countdownEndsAt: Long? = null,
    ) : GameplayUiState

    data class Unavailable(val reason: UnavailableReason) : GameplayUiState

    data class Finished(val scoreId: Long) : GameplayUiState
}

/**
 * A partida de [chartId] em [difficulty]. No celular, o Hilt cria pela `HiltGameplayViewModel`; no PC, o app.
 * [rankingTable]: a tabela do ranking em que a partida conta, pelas configurações lidas ao começar (no celular,
 * sempre a do celular; no PC, `RankingTable.pc(settings.controls.strum)`).
 *
 * Com [matchStart], a partida de sala: carregada, avisa ([MatchStart.loaded]), espera o começo combinado
 * ([MatchStart.awaitStart]), mostra a contagem e começa no instante devolvido, pelo [timeSource]; não pausa nunca e,
 * ao sair no meio ([leave]), avisa a sala ([MatchStart.left]). Com o [liveBoard] (só na de sala), o placar ao vivo:
 * publica o meu no começo, a cada [LIVE_PUBLISH_EVERY_MS] pelos quadros ([onFrame], no relógio da partida) e no fim
 * ([LiveBoard.publish] não segura a tela: o envio vai fora dela) e entrega o dos outros ([liveOthers]).
 */
@Suppress("TooManyFunctions") // a partida: carregar, pausar, ajustar, terminar e o modo de sala; um estado só
open class GameplayViewModel(
    private val chartId: String,
    private val difficulty: Difficulty,
    private val loader: ChartTrackLoader,
    private val settingsRepository: SettingsRepository,
    private val scores: ScoreRepository,
    private val chartOffsets: ChartOffsetRepository,
    private val songPlayers: SongPlayerFactory,
    private val stemPlayers: StemSongPlayerFactory,
    private val sfxPlayers: SfxPlayerFactory,
    private val timeSource: TimeSource,
    private val interruptionsFactory: PlaybackInterruptionsFactory,
    private val artImages: ChartArtImages,
    private val ranking: RankingRepository,
    private val rankingTable: (Settings) -> RankingTable = { RankingTable.PHONE },
    private val matchStart: MatchStart? = null,
    private val liveBoard: LiveBoard? = null,
    /** O log de depuração do começo junto (ver [matchSyncLog]); os testes trocam. */
    private val syncLog: (String) -> Unit = ::matchSyncLog,
) : ViewModel() {
    private val state = MutableStateFlow<GameplayUiState>(GameplayUiState.Loading)
    val uiState: StateFlow<GameplayUiState> = state.asStateFlow()

    /** O placar dos outros na partida de sala, ao vivo; null = partida sozinho (sem o bloco). */
    val liveOthers: StateFlow<List<LiveScore>>? = liveBoard?.others
        ?.catch { emit(emptyList()) } // uma falha na leitura não derruba a partida
        ?.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // O meu placar ao vivo (partida de sala): o motor da partida já começada, e quando publicar de novo.
    private var liveEngine: GameEngine? = null
    private var nextPublishAt = 0L

    private var player: SongPlayer? = null
    private var sfx: SfxPlayer? = null

    // Ligação, outro app tocando ou fone desconectado pausam a partida.
    private var interruptions: PlaybackInterruptions? = null
    private var finishing = false
    private var songOffsetMs = 0

    // O app pode ir para segundo plano enquanto a música carrega: a partida começa pausada.
    private var pauseRequested = false

    // Partida de sala: quando a sessão começou (no relógio da partida) e o instante combinado, até o som sair (o log
    // do atraso, ver [logSoundStart]).
    private var soundAskedAt: Long? = null
    private var agreedStartAt = 0L

    // Partida de sala: a sala já sabe que acabou para mim (saí, não abriu ou terminou); avisa uma vez só.
    private var roomDone = false

    // Os avisos à sala continuam depois de a tela fechar (o viewModelScope já foi cancelado), até o tempo-limite.
    private val roomReports by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    init {
        viewModelScope.launch { state.value = load() }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // cada falha vira uma explicação na tela
    private suspend fun load(): GameplayUiState = try {
        val playable = loader.read(chartId, difficulty)
        val settings = settingsRepository.settings.first()
        // Antes de a música tocar: ler a imagem leva tempo e a partida só aparece depois.
        val art = if (settings.showChartImage) artImages.loadOrNull(chartId) else null
        val (songPlayer, withoutSeparateGuitar) = prepareSong(playable.audio)
        songPlayer.setVolume(settings.masterVolume)
        val effects = sfxPlayers.create().also {
            sfx = it
            it.setVolumes(settings.effectiveHitVolume, settings.effectiveMissVolume)
        }
        songOffsetMs = chartOffsets.get(chartId)
        val offset = settings.audioOffsetMs.toLong() + songOffsetMs
        val session = GameSession(
            playable.notes,
            songPlayer,
            effects,
            timeSource,
            offset,
            // Sem som de acerto: sem guitarra separada a música toca inteira; com ela, a guitarra é a resposta.
            hitSounds = false,
            // O jogador veio jogar: volta um pouco para retomar no ritmo.
            rewindOnResumeMs = REWIND_ON_RESUME_MS,
            // No PC, com ou sem palheta (Configurações → Controles); no celular, sempre o toque.
            strum = settings.controls.strum,
        )
        val ready = GameplayUiState.Ready(
            playable.chart.title,
            playable.chart.artist,
            difficulty,
            session,
            settings,
            pauseRequested && matchStart == null,
            withoutSeparateGuitar,
            songOffsetMs = songOffsetMs,
            art = art,
        )
        if (matchStart == null) startSolo(session, ready) else startInRoom(matchStart, session, ready)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        release()
        // Os outros da sala não esperam por quem não conseguiu abrir a partida.
        reportToRoom { it.left() }
        GameplayUiState.Unavailable(reasonFor(e))
    }

    private fun startSolo(session: GameSession, ready: GameplayUiState.Ready): GameplayUiState.Ready {
        if (!pauseRequested) session.start()
        interruptions = interruptionsFactory.create().also {
            if (!pauseRequested) it.start(onInterrupted = ::pause, onBusy = ::pauseAudioBusy)
        }
        return ready
    }

    /**
     * A partida de sala: montada e parada até o começo combinado, com a contagem por cima; começa no instante
     * devolvido pelo [MatchStart] (no relógio da partida), esperando em passos pelo [timeSource] (o `delay` sozinho
     * pode acordar antes); com o começo já passado, começa na posição da música de agora (a dos outros). O foco de
     * áudio é pedido como sempre, mas perdê-lo não pausa.
     *
     * Logo depois do carregou (sem esperar o instante), o player é aquecido ([SongPlayer.warmUp]): o arranque do som
     * (no PC, ~650 ms na primeira vez) passa enquanto espera os outros e mede o relógio, não depois do instante; o
     * silêncio não se ouve. Atrasado, o reposicionamento abre outra saída, fria, e descarta o aquecimento (e a
     * latência dele não vale). Perto do instante, a latência que o player aquecido informa
     * ([SongPlayer.outputLatencyMs], até [MAX_OUTPUT_LATENCY_MS]) adianta o começo na mesma quantidade, para o som
     * sair no instante; sem ela, começa no instante, só com a calibração de sempre no relógio da música (Decisão 5b).
     */
    private suspend fun startInRoom(
        room: MatchStart,
        session: GameSession,
        ready: GameplayUiState.Ready,
    ): GameplayUiState.Ready {
        val waiting = ready.copy(inRoom = true, waitingForStart = true)
        state.value = waiting
        room.loaded()
        player?.warmUp()
        val startAt = room.awaitStart()
        // Carregou depois do começo (o começo já passou): começa onde os outros estão, por mais atrasado que seja. No
        // horário, do começo (a espera abaixo pode acordar uns milissegundos depois: não vale um reposicionamento).
        val late = timeSource.nowMs() > startAt
        val counting = waiting.copy(waitingForStart = false, countdownEndsAt = startAt)
        state.value = counting
        waitUntil(startAt - MAX_OUTPUT_LATENCY_MS)
        val latency = if (late) null else player?.outputLatencyMs
        waitUntil(startAt - (latency?.coerceIn(0, MAX_OUTPUT_LATENCY_MS) ?: 0))
        // Saiu enquanto contava (a tela já foi): nada de tocar num player liberado.
        if (player == null) return counting
        val now = timeSource.nowMs()
        session.start(fromMs = if (late) now - startAt else 0)
        syncLog("começo: latência informada=${latency ?: "?"} ms, start() ${now - startAt} ms do instante combinado")
        soundAskedAt = now
        agreedStartAt = startAt
        interruptions = interruptionsFactory.create().also { it.start(onInterrupted = {}, onBusy = {}) }
        startPublishing(session.engine)
        return counting
    }

    /** Espera até o instante [at] do relógio da partida (em passos: o `delay` pode acordar antes). */
    private suspend fun waitUntil(at: Long) {
        while (true) {
            val left = at - timeSource.nowMs()
            if (left <= 0) return
            delay(left)
        }
    }

    /**
     * Partida de sala, nos primeiros quadros: quando o som sai (o player deixa de estar arrancando), registra no log
     * quanto ele levou desde o `start()` e onde caiu em relação ao instante combinado (com a precisão de um quadro).
     */
    private fun logSoundStart() {
        val asked = soundAskedAt ?: return
        val song = player
        if (song == null) {
            soundAskedAt = null
            return
        }
        if (song.isStarting || !song.isPlaying) return
        val now = timeSource.nowMs()
        soundAskedAt = null
        syncLog("som saiu ${now - asked} ms depois do start(), ${now - agreedStartAt} ms do instante combinado")
    }

    /** O meu placar ao vivo já no começo (os outros me veem desde o 0); depois, pelos quadros ([onFrame]). */
    private fun startPublishing(engine: GameEngine) {
        val board = liveBoard ?: return
        liveEngine = engine
        nextPublishAt = timeSource.nowMs() + LIVE_PUBLISH_EVERY_MS
        board.publish(engine.liveScore(finished = false))
    }

    /**
     * A cada quadro da partida (depois de ela avançar): na de sala, registra quando o som saiu ([logSoundStart]) e
     * publica o meu placar se já passou
     * [LIVE_PUBLISH_EVERY_MS] desde o último. Barato e na thread da tela: só monta o placar e entrega ao
     * [LiveBoard.publish], que envia fora dela. Sem quadros (o app em segundo plano), não publica: os outros veem
     * "sem conexão", como a presença.
     */
    fun onFrame() {
        logSoundStart()
        val board = liveBoard ?: return
        val engine = liveEngine ?: return
        if (finishing) return
        val now = timeSource.nowMs()
        if (now < nextPublishAt) return
        nextPublishAt = now + LIVE_PUBLISH_EVERY_MS
        board.publish(engine.liveScore(finished = false))
    }

    /**
     * Saindo da partida (Sair, ou a tela fechando): na de sala, avisa que saí da rodada, se ela ainda não acabou para
     * mim. O aviso continua mesmo com a tela já fechada (até [ROOM_REPORT_TIMEOUT_MS]); sozinho, não faz nada.
     */
    fun leave() {
        liveEngine = null
        reportToRoom { it.left() }
    }

    /** Avisa a sala uma vez só (sair, não abrir ou terminar), sem ser cancelado pela tela que fecha. */
    private fun reportToRoom(report: suspend (MatchStart) -> Unit) {
        val room = matchStart ?: return
        if (roomDone) return
        roomDone = true
        roomReports.launch { withTimeoutOrNull(ROOM_REPORT_TIMEOUT_MS) { report(room) } }
    }

    /** Faixas separadas ou um arquivo só; se as faixas falharem, cai no arquivo só (com aviso). */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // a falha das faixas vira o modo de um arquivo
    private suspend fun prepareSong(audio: ChartAudio?): Pair<SongPlayer, Boolean> {
        val chartAudio = audio ?: throw NoAudioException()
        if (chartAudio !is ChartAudio.Stems) {
            return prepared(songPlayers.create(), chartAudio.singleUri, ::AudioFailedException) to false
        }
        val stems = stemPlayers.create(chartAudio.guitarUri, chartAudio.backingUris).also { player = it }
        try {
            stems.prepare(chartAudio.guitarUri)
            return stems to false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            stems.release()
            player = null
        }
        return prepared(songPlayers.create(), chartAudio.singleUri, ::AudioFailedException) to true
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun prepared(created: SongPlayer, uri: String, wrap: (Throwable) -> Exception): SongPlayer {
        player = created
        try {
            created.prepare(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw wrap(e)
        }
        return created
    }

    /** Pausa (o botão, o Voltar, a tecla, o segundo plano, as interrupções); na partida de sala, nunca. */
    fun pause() {
        if (matchStart != null) return
        if (state.value is GameplayUiState.Loading) pauseRequested = true
        if (player == null) return // já liberado: saindo ou terminando
        val ready = state.value as? GameplayUiState.Ready ?: return
        ready.session.pause()
        state.update { ready.copy(isPaused = true) }
    }

    private fun pauseAudioBusy() {
        pause()
        state.update { (it as? GameplayUiState.Ready)?.copy(audioBusy = true) ?: it }
    }

    fun resume() {
        if (matchStart != null) return
        if (state.value is GameplayUiState.Loading) pauseRequested = false
        if (player == null) return
        val ready = state.value as? GameplayUiState.Ready ?: return
        ready.session.resume()
        // Pede o foco de áudio de novo (uma ligação o tirou); negado, a pausa explica.
        interruptions?.start(onInterrupted = ::pause, onBusy = ::pauseAudioBusy)
        state.update { ready.copy(isPaused = false, audioBusy = false) }
    }

    /** Ajuste fino na pausa: vale ao continuar e fica salvo para este mapa. */
    fun adjustSongOffset(deltaMs: Int) {
        val ready = state.value as? GameplayUiState.Ready ?: return
        val updated = (songOffsetMs + deltaMs).coerceIn(-MAX_SONG_OFFSET_MS, MAX_SONG_OFFSET_MS)
        if (updated == songOffsetMs) return
        ready.session.adjustOffset((updated - songOffsetMs).toLong())
        songOffsetMs = updated
        state.update { ready.copy(songOffsetMs = updated) }
        viewModelScope.launch { chartOffsets.save(chartId, updated) }
    }

    fun onSessionFinished() {
        val ready = state.value as? GameplayUiState.Ready ?: return
        if (finishing) return
        finishing = true
        release()
        reportToRoom { it.finished() }
        val engine = ready.session.engine
        engine.finish()
        // O placar final (com as notas que faltavam já julgadas): depois dele, mais nenhum (finishing).
        if (liveEngine != null) liveBoard?.publish(engine.liveScore(finished = true))
        val record = ScoreRecord(
            id = 0,
            chartId = chartId,
            difficulty = difficulty,
            score = engine.score,
            accuracy = engine.accuracy(),
            maxCombo = engine.state.maxCombo,
            perfect = engine.count(Judgment.PERFECT),
            good = engine.count(Judgment.GOOD),
            miss = engine.count(Judgment.MISS),
            playedAtEpochMs = System.currentTimeMillis(),
            cleanStreakMs = engine.longestCleanMs,
            // As configurações de quando a partida começou: trocar a palheta depois não muda a tabela.
            rankingTable = rankingTable(ready.settings),
        )
        viewModelScope.launch {
            val scoreId = scores.save(record)
            // Também quando o Resultado é pulado: o envio é deste passo, não da tela (spec do ranking §4).
            ranking.submitIfBest(record.copy(id = scoreId), ready.title, ready.artist)
            state.value = GameplayUiState.Finished(scoreId)
        }
    }

    /** Para e libera o áudio, ao terminar ou quando a tela sai de vez ([onCleared]). */
    fun release() {
        interruptions?.stop()
        interruptions = null
        player?.release()
        sfx?.release()
        player = null
        sfx = null
    }

    override fun onCleared() {
        release()
        // A tela fechou sem o Sair (o Voltar durante o carregamento, o Android fechando): também saí da rodada.
        leave()
    }

    companion object {
        /**
         * O máximo que a partida de sala adianta a música pela latência informada pelo player: acima disso é leitura
         * errada da saída (o PC mediu ~75 ms; o celular, dezenas de ms).
         */
        const val MAX_OUTPUT_LATENCY_MS = 300L
    }
}

/** Quanto o aviso à sala ([MatchStart.left]/[MatchStart.finished]) pode demorar depois de a tela fechar. */
private const val ROOM_REPORT_TIMEOUT_MS = 10_000L

/** De quanto em quanto a partida de sala publica o meu placar ao vivo. */
internal const val LIVE_PUBLISH_EVERY_MS = 1_000L

/**
 * O meu placar ao vivo agora (o uid e o nome a sala sabe). A precisão, no meio da partida, é sobre as notas que já
 * passaram (o BOM vale meia, como no [GameEngine.accuracy]); no fim, a do motor (sobre o mapa inteiro).
 */
private fun GameEngine.liveScore(finished: Boolean): LiveScore {
    val hits = count(Judgment.PERFECT) + count(Judgment.GOOD)
    val judged = hits + count(Judgment.MISS)
    val accuracy = when {
        finished -> accuracy()
        judged == 0 -> 0f
        else -> (count(Judgment.PERFECT) + GOOD_WEIGHT * count(Judgment.GOOD)) / judged
    }
    return LiveScore(
        uid = "",
        name = "",
        score = score,
        combo = combo,
        maxCombo = state.maxCombo,
        accuracy = accuracy,
        finished = finished,
        hits = hits,
        notes = judged,
    )
}

/** Quanto o BOM vale na precisão (como no [GameEngine.accuracy]). */
private const val GOOD_WEIGHT = 0.5f

/** Quanto a pista volta ao continuar depois de uma pausa. */
private const val REWIND_ON_RESUME_MS = 500L

/** Falha na leitura do arquivo do mapa (corrompido ou mudou desde a importação). */
private class ChartReadException(cause: Throwable) : Exception("mapa ilegível", cause)

/** Falhas da leitura do mapa ficam separadas das outras (sons, banco): cada uma tem a sua explicação. */
@Suppress("TooGenericExceptionCaught")
private suspend fun ChartTrackLoader.read(chartId: String, difficulty: Difficulty) = try {
    load(chartId, difficulty)
} catch (e: CancellationException) {
    throw e
} catch (e: SecurityException) {
    // Permissão da pasta revogada no meio da leitura.
    throw FolderAccessLostException(e.message.orEmpty(), e)
} catch (e: ChartNotFoundException) {
    throw e
} catch (e: FolderAccessLostException) {
    throw e
} catch (e: Exception) {
    throw ChartReadException(e)
}

/** A explicação na tela para cada falha ao preparar a partida. */
private fun reasonFor(error: Exception): UnavailableReason = when (error) {
    is NoAudioException -> UnavailableReason.NO_AUDIO

    is AudioFailedException -> UnavailableReason.AUDIO_FAILED

    is FolderAccessLostException -> UnavailableReason.FOLDER_ACCESS_LOST

    is ChartNotFoundException -> UnavailableReason.CHART_MISSING

    // O arquivo do mapa não pôde ser lido (corrompido ou mudou desde a importação).
    is ChartReadException -> UnavailableReason.CHART_UNREADABLE

    else -> UnavailableReason.UNEXPECTED
}

private class NoAudioException : Exception("pasta sem áudio")

private class AudioFailedException(cause: Throwable) : Exception("áudio não pôde ser preparado", cause)
