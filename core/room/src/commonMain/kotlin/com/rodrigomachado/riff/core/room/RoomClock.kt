package com.rodrigomachado.riff.core.room

/**
 * O relógio comum da sala: a hora do servidor estimada pelo relógio deste aparelho ([clock]) mais o [offsetMs]
 * medido por [RoomRepository.measureClock]. O começo da partida vem na hora do servidor e cada aparelho começa no
 * instante equivalente do seu relógio ([toLocal]).
 */
class RoomClock(val offsetMs: Long, private val clock: () -> Long) {
    /** A hora do servidor agora. */
    fun serverNow(): Long = clock() + offsetMs

    /** O instante do relógio deste aparelho que corresponde à hora do servidor [serverMs]. */
    fun toLocal(serverMs: Long): Long = serverMs - offsetMs
}
