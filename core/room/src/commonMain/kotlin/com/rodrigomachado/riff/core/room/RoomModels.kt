package com.rodrigomachado.riff.core.room

/** O aparelho do jogador, como fica no banco ([wire]). */
enum class RoomDevice(val wire: String) {
    PHONE("celular"),
    PC("pc"),
    ;

    companion object {
        fun of(wire: String?): RoomDevice = entries.firstOrNull { it.wire == wire } ?: PHONE
    }
}

/**
 * Um jogador da sala. Os tempos são do servidor; [gone] = sem escrever há mais de 60 s (o batimento é a cada 20 s),
 * pelo relógio do aparelho.
 */
data class RoomPlayer(
    val uid: String,
    val name: String,
    val device: RoomDevice,
    val joinedAt: Long,
    val seenAt: Long,
    val gone: Boolean,
)

/** A sala vista pelo jogador [me]: os [players] na ordem em que entraram. */
data class Room(val code: String, val owner: String, val players: List<RoomPlayer>, val me: String) {
    val iAmOwner: Boolean get() = owner == me
}

sealed interface CreateResult {
    data class Created(val code: String) : CreateResult

    /** Sem internet, ou o Firebase recusou ou falhou: tentar de novo. */
    data object NoConnection : CreateResult
}

sealed interface JoinResult {
    data class Joined(val code: String) : JoinResult

    data object NotFound : JoinResult

    /** As 4 vagas ocupadas. */
    data object Full : JoinResult

    /** Sem internet, ou o Firebase recusou ou falhou: tentar de novo. */
    data object NoConnection : JoinResult
}
