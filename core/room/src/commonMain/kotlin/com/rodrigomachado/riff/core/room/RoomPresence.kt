package com.rodrigomachado.riff.core.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/**
 * A presença do jogador na sala em que ele está ([RoomRepository.joinedRoom]), qualquer que seja a tela à vista (a
 * sala, a partida, o Resultado, a Biblioteca de escolha), num escopo ([scope]) que vive mais que as telas: no celular,
 * um só no app (Hilt), parado com o app em segundo plano ([setForeground]); no PC, enquanto a janela existe.
 *
 * - O batimento a cada 20 s ([RoomRepository.heartbeat]): sem ele, quem está na partida "some" aos 60 s (outro
 *   assume o dono) e sai da sala aos 5 min. Sendo o dono, o batimento também desfaz a rodada órfã.
 * - O Pronto que sobrou: de volta à sala depois de uma rodada, ou com outra música, o meu Pronto volta a "não"
 *   ([RoomRepository.clearReady]), mesmo sem a tela da sala à vista.
 *
 * Sair da sala ([RoomRepository.leave]) para os dois na hora. A sala que ela observa fica em [room] (o placar ao vivo
 * da partida lê dela, sem abrir outro streaming).
 */
@OptIn(ExperimentalCoroutinesApi::class) // collectLatest: trocar de sala (ou sair) cancela a presença da anterior
class RoomPresence(
    private val repository: RoomRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val foreground = MutableStateFlow(true)
    private val seen = MutableStateFlow<Room?>(null)

    /** A sala em que estou, como a presença a vê agora (relida a cada 5 s); null = fora da sala ou em segundo plano. */
    val room: StateFlow<Room?> = seen.asStateFlow()

    /** O que esta presença viu de cada sala (para o Pronto que sobrou): continua entre o segundo plano e a volta. */
    private var tracked: Pair<String, StaleReady>? = null

    init {
        scope.launch {
            combine(repository.joinedRoom, foreground) { code, visible -> code.takeIf { visible } }
                .distinctUntilChanged()
                .collectLatest { code ->
                    seen.value = null
                    code?.let { present(it) }
                }
        }
    }

    /** O app à vista (true) ou em segundo plano (false: sem batimento, como se tivesse fechado). */
    fun setForeground(visible: Boolean) {
        foreground.value = visible
    }

    private suspend fun present(code: String) = coroutineScope {
        val stale = tracked?.takeIf { it.first == code }?.second ?: StaleReady().also { tracked = code to it }
        launch {
            while (true) {
                repository.heartbeat(code)
                delay(HEARTBEAT_MS)
            }
        }
        repository.observe(code)
            // Sem internet ou o login recusado: tenta de novo (o batimento segue por conta própria).
            .retryWhen { _, _ ->
                delay(RETRY_MS)
                true
            }
            .collect { room ->
                seen.value = room
                if (room != null && stale.mustClear(room) && repository.clearReady(code)) stale.cleared()
            }
    }

    private companion object {
        const val HEARTBEAT_MS = 20_000L
        const val RETRY_MS = 5_000L
    }
}

/**
 * Se o meu Pronto sobrou de antes: a sala voltou à escolha numa rodada mais nova do que a do Pronto, ou a música
 * mudou. Na primeira vez que vê a sala, o Pronto de agora vale (a sala aberta já com ele), a não ser que a sala esteja
 * no meio de uma rodada (então ele é dessa rodada). Fica pedindo a limpeza até ela ser gravada ou o Pronto sumir.
 */
internal class StaleReady {
    private var started = false
    private var round = 0
    private var chart: String? = null
    private var pending = false

    fun mustClear(room: Room): Boolean {
        val me = room.players.firstOrNull { it.uid == room.me } ?: return false
        val chartNow = room.song?.chartId
        if (!started) {
            started = true
            round = if (room.state == RoomState.LOBBY) room.round else room.round - 1
            chart = chartNow
            return false
        }
        if (chartNow != chart) {
            chart = chartNow
            pending = true
        }
        if (room.state == RoomState.LOBBY && room.round > round) {
            round = room.round
            pending = true
        }
        if (pending && room.state == RoomState.LOBBY && !me.ready) pending = false
        return pending && room.state == RoomState.LOBBY
    }

    fun cleared() {
        pending = false
    }
}
