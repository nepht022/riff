package com.rodrigomachado.riff.feature.gameplay

/**
 * O começo combinado de uma partida de sala (Decisão 5 do plano da M2). A partida não conhece a sala nem o Firebase:
 * quem implementa é o `:feature:room` (`RoomMatchStart`), montado pelo app ([MatchStartFactory]).
 *
 * Os instantes são do relógio da partida (o `TimeSource` do [GameplayViewModel], o mesmo da `GameSession` e do
 * `SongClock`: no celular, `SystemClock.elapsedRealtime`), nunca o relógio de parede nem a hora do servidor.
 */
interface MatchStart {
    /** A música carregou e a partida está montada, parada. */
    suspend fun loaded()

    /** Espera o começo combinado; devolve o instante no relógio da partida (ms, o mesmo do `TimeSource`). */
    suspend fun awaitStart(): Long

    /** O jogador saiu no meio da rodada (ou nem conseguiu abrir a partida). */
    suspend fun left()

    /** A música terminou (o Resultado vem a seguir): o dono devolve a sala à escolha da música (Decisão 4). */
    suspend fun finished()
}

/** Monta o [MatchStart] da sala [code], rodada [round] (pelo app, que conhece a sala; a partida não). */
fun interface MatchStartFactory {
    fun create(code: String, round: Int): MatchStart
}
