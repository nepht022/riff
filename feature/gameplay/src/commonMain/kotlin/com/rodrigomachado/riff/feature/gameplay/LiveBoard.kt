package com.rodrigomachado.riff.feature.gameplay

import kotlinx.coroutines.flow.Flow

/**
 * O placar ao vivo da partida de sala (Decisão 2 do plano da M3). A partida não conhece a sala nem o Firebase: quem
 * implementa é o `:feature:room` (`RoomLiveBoard`), montado pelo app ([LiveBoardFactory]).
 */
interface LiveBoard {
    /** O placar dos outros desta rodada, ao vivo, na ordem do placar (sem o meu). */
    val others: Flow<List<LiveScore>>

    /**
     * O meu, chamado a cada 1 s pela partida (e no fim com `finished = true`), na thread da tela; não bloqueia: o
     * envio vai fora dela e, com um envio ainda em andamento, só o último espera a vez (sem empilhar).
     */
    fun publish(mine: LiveScore)
}

/**
 * Uma linha do placar ao vivo: os pontos, o combo de agora e o maior, a [accuracy] de 0 a 1 e se a música acabou
 * ([finished]); [left] = saiu da rodada; [stale] = "sem conexão" (sem placar novo há 15 s). [hits] e [notes] (as notas
 * acertadas de quantas já passaram) só vão no meu, para o Resultado da sala; o [uid] e o [name] do meu ficam vazios
 * (a sala sabe quem sou).
 */
data class LiveScore(
    val uid: String,
    val name: String,
    val score: Long,
    val combo: Int,
    val maxCombo: Int,
    val accuracy: Float,
    val finished: Boolean,
    val left: Boolean = false,
    val stale: Boolean = false,
    val hits: Int = 0,
    val notes: Int = 0,
)

/** Monta o [LiveBoard] da sala [code], rodada [round] (pelo app, que conhece a sala; a partida não). */
fun interface LiveBoardFactory {
    fun create(code: String, round: Int): LiveBoard
}

/** Uma linha do bloco na tela: a [position] (1 = primeiro), o placar e se é a minha. */
internal data class LiveLine(val position: Int, val score: LiveScore, val mine: Boolean) {
    /** A chave da linha (a mesma entre as leituras, para ela deslizar quando a posição muda). */
    val key: String get() = if (mine) GameplayTestTags.LIVE_ME else score.uid
}

/** Quantas linhas o bloco mostra (a sala tem até 4 jogadores). */
internal const val LIVE_MAX_LINES = 4

/**
 * As linhas do bloco (Decisão 3): pontos (desc), empate pelo maior combo; quem saiu sempre por último. A ordem dos
 * [others] (a do placar da sala: quem entrou primeiro desempata) é mantida entre empatados, e o meu fica depois dos
 * empatados com ele, para nenhuma linha piscar. Com mais de [LIVE_MAX_LINES], a minha entra no lugar da última.
 */
internal fun liveStandings(others: List<LiveScore>, mine: LiveScore): List<LiveLine> {
    val all = others.map { it to false } + (mine to true)
    val sorted = all.sortedWith(
        compareBy<Pair<LiveScore, Boolean>> { it.first.left }
            .thenByDescending { it.first.score }
            .thenByDescending { it.first.maxCombo },
    )
    val lines = sorted.mapIndexed { index, (score, isMine) -> LiveLine(index + 1, score, isMine) }
    if (lines.size <= LIVE_MAX_LINES) return lines
    val top = lines.take(LIVE_MAX_LINES)
    return if (top.any { it.mine }) top else top.dropLast(1) + lines.first { it.mine }
}
