package com.rodrigomachado.riff.feature.room

import com.rodrigomachado.riff.core.room.NoConnection
import com.rodrigomachado.riff.core.room.Room
import com.rodrigomachado.riff.core.room.RoomClock
import com.rodrigomachado.riff.core.room.RoomRepository
import com.rodrigomachado.riff.core.room.RoomState
import com.rodrigomachado.riff.feature.gameplay.MatchStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * O começo da partida da sala [code], rodada [round], pelo [RoomRepository] (Decisões 2–4 do plano da M2):
 * - [loaded] mede o relógio do servidor de novo ([RoomRepository.measureClock]) e marca o `carregou` da rodada;
 * - [awaitStart] espera o `inicio` aparecer na sala observada; enquanto espera, se este aparelho é o dono (também o
 *   dono novo que assumiu no meio), confere a cada 1 s se já dá para marcar o começo
 *   ([RoomRepository.scheduleStartIfDue]);
 * - [left] marca o `saiu`; o dono, ao sair ou terminar ([finished]), devolve a sala à escolha da música.
 *
 * [clock] é o relógio da partida (o `TimeSource` da `GameSession`: no celular, `SystemClock.elapsedRealtime`), que não
 * é o relógio de parede do [RoomRepository] (`System.currentTimeMillis`). O instante devolvido é convertido direto da
 * hora do servidor para ele: `clock() + (inicio − serverNow())`, lendo os dois relógios no mesmo momento; assim o
 * relógio de parede errado (o offset compensa) ou acertado pela rede depois da medida não muda o começo.
 *
 * Sem começo combinado (a sala acabou, a rodada voltou à sala ou passou, sem internet, ou nada em [MAX_WAIT_MS]), a
 * partida começa já: ela nunca fica presa esperando.
 */
class RoomMatchStart(
    private val code: String,
    private val round: Int,
    private val repository: RoomRepository,
    private val clock: () -> Long,
) : MatchStart {
    @Volatile
    private var roomClock: RoomClock? = null

    /** A última sala vista (para saber se sou o dono sem ler de novo, quando não der para ler). */
    @Volatile
    private var lastRoom: Room? = null

    override suspend fun loaded() {
        roomClock = measure()
        repository.markLoaded(code, round)
    }

    override suspend fun awaitStart(): Long {
        val serverStart = withTimeoutOrNull(MAX_WAIT_MS) { agreedStart() } ?: return clock()
        val server = roomClock ?: measure() ?: return clock()
        return clock() + (serverStart - server.serverNow())
    }

    override suspend fun left() {
        repository.markLeft(code, round)
        if (iAmOwner()) repository.backToLobby(code)
    }

    override suspend fun finished() {
        if (iAmOwner()) repository.backToLobby(code)
    }

    /** O `inicio` desta rodada (hora do servidor); null = não vai haver (ver a classe). */
    private suspend fun agreedStart(): Long? = coroutineScope {
        val seen = MutableStateFlow<Room?>(null)
        val owner = launch {
            while (true) {
                seen.first { it?.iAmOwner == true }
                repository.scheduleStartIfDue(code, round)
                delay(SCHEDULE_EVERY_MS)
            }
        }
        try {
            val room = repository.observe(code)
                .onEach {
                    lastRoom = it ?: lastRoom
                    seen.value = it
                }
                .first(::decided)
            room?.startAt?.takeIf { room.round == round && room.state == RoomState.PLAYING }
        } catch (_: NoConnection) {
            null
        } finally {
            owner.cancel()
        }
    }

    /** A sala já decidiu esta rodada: o começo marcado, ou não vai haver (acabou, voltou à sala, outra rodada). */
    private fun decided(room: Room?): Boolean = room == null || room.round != round ||
        room.state == RoomState.LOBBY || (room.state == RoomState.PLAYING && room.startAt != null)

    private suspend fun measure(): RoomClock? = try {
        repository.measureClock(code)
    } catch (_: NoConnection) {
        null
    }

    /** Se sou o dono agora (lido de novo: o dono pode ter mudado); sem ler, pela última sala vista. */
    private suspend fun iAmOwner(): Boolean {
        val room = try {
            repository.observe(code).first()
        } catch (_: NoConnection) {
            lastRoom
        }
        return room?.iAmOwner == true
    }

    companion object {
        /** De quanto em quanto o dono confere se já dá para marcar o começo. */
        const val SCHEDULE_EVERY_MS = 1_000L

        /**
         * O máximo que a partida espera o começo: os 15 s do carregamento e os 3 s da contagem, com folga. O dono que
         * sumiu leva mais de 60 s para ser trocado (M1): a partida não espera tanto, começa sozinha.
         */
        const val MAX_WAIT_MS = 30_000L
    }
}
