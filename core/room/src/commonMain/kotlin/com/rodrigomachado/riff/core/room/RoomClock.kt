package com.rodrigomachado.riff.core.room

/**
 * O relógio comum da sala: a hora do servidor estimada pelo relógio deste aparelho ([clock]) mais o [offsetMs]
 * medido por [RoomRepository.measureClock]. O começo da partida vem na hora do servidor e cada aparelho começa no
 * instante equivalente do seu relógio ([toLocal]). [roundTripMs] é a ida e volta da medida usada (o erro do offset
 * fica abaixo da metade dela): só para o log de depuração do começo junto.
 */
class RoomClock(val offsetMs: Long, private val clock: () -> Long, val roundTripMs: Long = 0) {
    /** A hora do servidor agora. */
    fun serverNow(): Long = clock() + offsetMs

    /** O instante do relógio deste aparelho que corresponde à hora do servidor [serverMs]. */
    fun toLocal(serverMs: Long): Long = serverMs - offsetMs
}
