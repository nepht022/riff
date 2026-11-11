package com.rodrigomachado.riff.feature.room

import com.rodrigomachado.riff.core.room.BoardLine
import com.rodrigomachado.riff.core.room.Room
import com.rodrigomachado.riff.core.room.RoomRepository
import com.rodrigomachado.riff.core.room.RoundScore
import com.rodrigomachado.riff.feature.gameplay.LiveBoard
import com.rodrigomachado.riff.feature.gameplay.LiveScore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * O placar ao vivo da sala [code], rodada [round], na partida (Decisão 2 do plano da M3):
 * - [others]: o placar da rodada ([Room.boardOf], já na ordem, com "saiu" e "sem conexão") sem a minha linha, lido da
 *   sala que a presença observa ([rooms]: `RoomPresence.room`), sem abrir outro streaming;
 * - [publish]: o meu vai pelo [RoomRepository.publishScore] no [scope] (fora da thread da tela). Com um envio em
 *   andamento (a rede lenta ou caída), os seguintes não empilham: fica guardado só o último, que vai quando o envio
 *   acaba; o final (`finished`) nunca se perde, porque é sempre o último.
 *
 * O [scope] vive mais que a partida: o envio final continua depois de a tela fechar. Um erro inesperado no envio só
 * vai para o [log] (com o [CoroutineExceptionHandler]): no celular, uma exceção sem tratamento no escopo fecharia o
 * app; o próximo [publish] envia de novo.
 */
class RoomLiveBoard internal constructor(
    private val code: String,
    private val round: Int,
    rooms: Flow<Room?>,
    private val scope: CoroutineScope,
    private val log: (String, Throwable) -> Unit = ::roomLog,
    private val send: suspend (RoundScore) -> Unit,
) : LiveBoard {
    constructor(
        code: String,
        round: Int,
        repository: RoomRepository,
        rooms: Flow<Room?>,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    ) : this(code, round, rooms, scope, send = { repository.publishScore(code, round, it) })

    private val onError = CoroutineExceptionHandler { _, error -> log("placar ao vivo: o envio falhou", error) }

    private val lock = Any()
    private var pending: LiveScore? = null
    private var busy = false

    override val others: Flow<List<LiveScore>> = rooms
        .filterNotNull()
        .map { room -> if (room.code == code) room.boardOf(round).filter { it.uid != room.me }.map(::live) else null }
        .filterNotNull()
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    override fun publish(mine: LiveScore) {
        synchronized(lock) {
            pending = mine
            if (busy) return
            busy = true
        }
        scope.launch(onError) {
            var drained = false
            try {
                while (true) {
                    val next = takePending() ?: break
                    send(next.toRoundScore())
                }
                drained = true
            } finally {
                // Cancelado ou com erro no meio: libera para o próximo (esvaziado, o takePending já liberou).
                if (!drained) synchronized(lock) { busy = false }
            }
        }
    }

    /** O próximo a enviar; sem nenhum, libera o envio (na mesma trava: um [publish] no meio não se perde). */
    private fun takePending(): LiveScore? = synchronized(lock) {
        val next = pending
        pending = null
        if (next == null) busy = false
        next
    }
}

private fun live(line: BoardLine) = LiveScore(
    uid = line.uid,
    name = line.name,
    score = line.score,
    combo = line.combo,
    maxCombo = line.maxCombo,
    accuracy = line.accuracy,
    finished = line.finished,
    left = line.left,
    stale = line.stale,
)

private fun LiveScore.toRoundScore() = RoundScore(
    score = score,
    combo = combo,
    maxCombo = maxCombo,
    hits = hits,
    notes = notes,
    accuracy = accuracy,
    finished = finished,
)
