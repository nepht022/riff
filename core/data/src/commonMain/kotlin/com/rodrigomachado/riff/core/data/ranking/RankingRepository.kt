package com.rodrigomachado.riff.core.data.ranking

import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.database.RankingPendingDao
import com.rodrigomachado.riff.core.database.RankingPendingEntity
import com.rodrigomachado.riff.core.model.ScoreRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
/** Ranking online (spec do ranking): nome do jogador, envio das melhores partidas e as listas. */
interface RankingRepository {
    /** Sem `ranking.url` no build, o ranking fica desligado. */
    val isConfigured: Boolean

    val player: Flow<Player>

    /** "Agora não" no pedido do nome: só até o app ser aberto de novo. */
    val askedLater: StateFlow<Boolean>

    fun askLater()

    /** Reserva (ou troca) o nome; no primeiro, envia uma vez os melhores do histórico deste celular. */
    suspend fun register(name: String): NameResult

    /** Depois de salvar o placar: se for a melhor deste celular em algum número, vai para o ranking. */
    fun submitIfBest(record: ScoreRecord, title: String, artist: String)

    fun statusFor(scoreId: Long): StateFlow<SubmitStatus?>

    /** Manda o que ficou na fila (sem internet, ou sem nome ainda). */
    suspend fun flush()

    suspend fun songs(query: String): ApiAnswer<List<RankedSong>>

    /** Todas as linhas do mapa; o placar é montado no celular ([rankBoard]). */
    suspend fun board(chartId: String): ApiAnswer<List<BoardEntry>>
}

@Suppress("LongParameterList")
@Singleton
class AppsScriptRankingRepository(
    private val api: RankingApi,
    private val players: PlayerStore,
    private val history: ScoreHistory,
    private val pending: RankingPendingDao,
    private val charts: ChartRepository,
    private val scope: CoroutineScope,
    private val keys: () -> String,
) : RankingRepository {
    @Inject constructor(
        api: RankingApi,
        players: PlayerStore,
        history: ScoreHistory,
        pending: RankingPendingDao,
        charts: ChartRepository,
        @RankingScope scope: CoroutineScope,
    ) : this(api, players, history, pending, charts, scope, { UUID.randomUUID().toString() })

    private val statuses = ConcurrentHashMap<Long, MutableStateFlow<SubmitStatus?>>()
    private val sending = Mutex()
    private val later = MutableStateFlow(false)
    private val sender = PendingSender(api, pending) { scoreId, placed -> statusOf(scoreId).value = placed }

    override val isConfigured: Boolean get() = api.isConfigured
    override val player: Flow<Player> get() = players.player
    override val askedLater: StateFlow<Boolean> = later.asStateFlow()

    override fun askLater() {
        later.value = true
    }

    override suspend fun register(name: String): NameResult {
        if (!api.isConfigured) return NameResult.Offline
        val clean = name.trim()
        if (!PlayerNames.isValid(clean)) return NameResult.Invalid
        val before = players.player.first()
        // A chave fica guardada antes de reservar: se o script gravar e a resposta se perder, a próxima tentativa
        // é do mesmo celular (senão o próprio nome viraria "em uso").
        val key = before.key ?: keys().also { players.saveKey(it) }
        return when (val answer = api.reserve(clean, key)) {
            is ApiAnswer.Ok -> {
                players.save(answer.value, key)
                if (before.name == null) queueHistory()
                scope.launch { flush() }
                NameResult.Ok
            }

            is ApiAnswer.Refused -> when (answer.reason) {
                "nome_em_uso" -> NameResult.Taken
                "limite" -> NameResult.Limit
                else -> NameResult.Invalid
            }

            ApiAnswer.Offline -> NameResult.Offline
        }
    }

    override fun submitIfBest(record: ScoreRecord, title: String, artist: String) {
        if (!api.isConfigured) return
        val status = statusOf(record.id)
        scope.launch {
            val earlier = history.of(record.chartId, record.difficulty).filter { it.id != record.id }
            if (!record.improves(earlier)) {
                status.value = SubmitStatus.NotBest
                flush() // o que ficou na fila sai ao terminar qualquer partida (spec §4)
                return@launch
            }
            pending.insert(record.toSubmission(title, artist).toPending(record.id))
            if (players.player.first().name == null) {
                status.value = SubmitStatus.NoName
                return@launch
            }
            status.value = SubmitStatus.Sending
            flush()
            if (status.value == SubmitStatus.Sending) {
                // Sem nome depois do envio: o jogador foi apagado do ranking e outra pessoa pegou o nome.
                status.value = if (players.player.first().name == null) SubmitStatus.NoName else SubmitStatus.Queued
            }
        }
    }

    override fun statusFor(scoreId: Long): StateFlow<SubmitStatus?> = statusOf(scoreId)

    override suspend fun flush() = sending.withLock {
        var registeredAgain = false
        while (true) {
            val (name, key) = players.player.first()
            if (name == null || key == null) return@withLock
            val outcome = sender.send(key)
            if (outcome == Outcome.REMOVED) return@withLock leave()
            // O dono apagou o jogador da planilha: reserva o mesmo nome de novo (uma vez) e tenta outra vez.
            if (outcome != Outcome.NO_NAME || registeredAgain || !registerAgain(name, key)) return@withLock
            registeredAgain = true
        }
    }

    /** O mesmo nome de novo; se outra pessoa já pegou, esquece o nome para o pedido aparecer outra vez. */
    private suspend fun registerAgain(name: String, key: String): Boolean = when (val answer = api.reserve(name, key)) {
        is ApiAnswer.Ok -> true.also { players.save(answer.value, key) }

        is ApiAnswer.Refused -> false.also {
            if (answer.reason == "nome_em_uso") players.forgetName()
            if (answer.reason == "removido") leave()
        }

        ApiAnswer.Offline -> false
    }

    /** Pedido de exclusão atendido pelo dono: sem nome, sem chave e sem nada na fila (não volta sozinho). */
    private suspend fun leave() {
        players.forget()
        pending.all().forEach { pending.delete(it.id) }
    }

    override suspend fun songs(query: String) = api.songs(players.player.first().key.orEmpty(), query)

    override suspend fun board(chartId: String) = api.board(players.player.first().key.orEmpty(), chartId)

    /** Os melhores de cada número por mapa e dificuldade, inclusive os de antes de ter nome. */
    private suspend fun queueHistory() {
        history.all().groupBy { it.chartId to it.difficulty }.forEach { (target, records) ->
            val chart = charts.getChart(target.first) ?: return@forEach // mapa que saiu da biblioteca
            pending.insert(records.best(chart.title, chart.artist).toPending(scoreId = null))
        }
    }

    private fun statusOf(scoreId: Long) = statuses.getOrPut(scoreId) { MutableStateFlow(null) }
}

/** Melhor que o histórico em pelo menos um número do ranking (o histórico vazio conta como melhor). */
private fun ScoreRecord.improves(earlier: List<ScoreRecord>): Boolean {
    if (earlier.isEmpty()) return true
    val clean = cleanStreakMs
    val bestClean = earlier.mapNotNull { it.cleanStreakMs }.maxOrNull()
    return score > earlier.maxOf { it.score } ||
        accuracy > earlier.maxOf { it.accuracy } ||
        maxCombo > earlier.maxOf { it.maxCombo } ||
        perfect > earlier.maxOf { it.perfect } ||
        (clean != null && (bestClean == null || clean > bestClean))
}

private fun List<ScoreRecord>.best(title: String, artist: String) = Submission(
    first().chartId,
    first().difficulty,
    title,
    artist,
    maxOf { it.score },
    maxOf { it.accuracy },
    maxOf { it.maxCombo },
    maxOf { it.perfect },
    mapNotNull { it.cleanStreakMs }.maxOrNull(),
    maxOf { it.notes },
)

/** Título e artista vêm do mapa, de qualquer tamanho: cortados, a pontuação cabe no pedido do script (4000). */
private fun ScoreRecord.toSubmission(title: String, artist: String) = Submission(
    chartId,
    difficulty,
    title.take(MAX_NAME_CHARS),
    artist.take(MAX_NAME_CHARS),
    score,
    accuracy,
    maxCombo,
    perfect,
    cleanStreakMs,
    notes,
)

private const val MAX_NAME_CHARS = 200

/** Todas as notas da partida: no fim, cada uma foi perfeita, boa ou erro. */
private val ScoreRecord.notes: Int get() = perfect + good + miss

private fun Submission.toPending(scoreId: Long?) = RankingPendingEntity(
    scoreId = scoreId,
    chartId = chartId,
    difficulty = difficulty.name,
    title = title,
    artist = artist,
    points = points,
    accuracy = accuracy,
    combo = combo,
    perfect = perfect,
    cleanMs = cleanMs,
    createdAtEpochMs = System.currentTimeMillis(),
    notes = notes,
)

/** O escopo do app para o ranking: o envio continua depois de a tela da partida sair. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RankingScope
