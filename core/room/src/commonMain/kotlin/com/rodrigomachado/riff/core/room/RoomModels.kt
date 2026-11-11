package com.rodrigomachado.riff.core.room

import com.rodrigomachado.riff.core.model.Difficulty

/** O aparelho do jogador, como fica no banco ([wire]). */
enum class RoomDevice(val wire: String) {
    PHONE("celular"),
    PC("pc"),
    ;

    companion object {
        fun of(wire: String?): RoomDevice = entries.firstOrNull { it.wire == wire } ?: PHONE
    }
}

/** Se o jogador tem a música da sala, como fica no banco ([wire]). */
enum class SongStatus(val wire: String) {
    HAS("tem"),
    DOWNLOADING("baixando"),
    MISSING("sem"),
    ;

    companion object {
        fun of(wire: String?): SongStatus? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * O momento da sala, como fica no banco ([wire]): escolhendo a música, na contagem (a partida carregando) ou na
 * partida (com o começo marcado). O `"fim"` das regras ainda não é usado (M3) e é lido como [LOBBY].
 */
enum class RoomState(val wire: String) {
    LOBBY("sala"),
    COUNTDOWN("contagem"),
    PLAYING("partida"),
    ;

    companion object {
        fun of(wire: String?): RoomState = entries.firstOrNull { it.wire == wire } ?: LOBBY
    }
}

/** A música veio do Encore: o que é preciso para baixar o mesmo pacote ([md5]) noutro aparelho. */
data class EncoreRef(val md5: String, val video: Boolean, val charter: String?)

/** A música escolhida pelo dono; [chartId] é o SHA-1 das notas (o mesmo em todos os aparelhos). */
data class RoomSong(
    val chartId: String,
    val title: String,
    val artist: String,
    val difficulties: Set<Difficulty>,
    val durationMs: Long?,
    val encore: EncoreRef?,
)

/**
 * Um jogador da sala. Os tempos são do servidor; [gone] = sem escrever há mais de 60 s (o batimento é a cada 20 s),
 * pelo relógio do aparelho. Os campos da M2 são o estado dele para a música da sala: [song] null = ainda não
 * conferiu; [loadedRound]/[leftRound] = a rodada em que a música carregou / que ele abandonou.
 */
data class RoomPlayer(
    val uid: String,
    val name: String,
    val device: RoomDevice,
    val joinedAt: Long,
    val seenAt: Long,
    val gone: Boolean,
    val difficulty: Difficulty? = null,
    val ready: Boolean = false,
    val song: SongStatus? = null,
    val loadedRound: Int? = null,
    val leftRound: Int? = null,
)

/**
 * O placar de um jogador numa rodada, como ele manda ([RoomRepository.publishScore]): os pontos, o combo de agora e o
 * maior, as notas acertadas de quantas já passaram, a [accuracy] de 0 a 1 e se a música dele acabou ([finished]).
 */
data class RoundScore(
    val score: Long,
    val combo: Int,
    val maxCombo: Int,
    val hits: Int,
    val notes: Int,
    val accuracy: Float,
    val finished: Boolean,
)

/**
 * Uma linha do placar da rodada ([Room.board]), na ordem do placar. [left] = saiu da rodada (ou da sala); [stale] =
 * "sem conexão": não terminou e não manda o placar há 15 s (ou nunca mandou, 15 s depois do começo), pela hora do
 * servidor estimada na leitura. Quem saiu da sala antes de este aparelho vê-lo fica com o [name] vazio.
 */
data class BoardLine(
    val uid: String,
    val name: String,
    val device: RoomDevice,
    val score: Long,
    val combo: Int,
    val maxCombo: Int,
    val accuracy: Float,
    val finished: Boolean,
    val left: Boolean,
    val stale: Boolean,
)

/**
 * A sala vista pelo jogador [me]: os [players] na ordem em que entraram, a música, a rodada ([round] 0 = nenhuma
 * ainda) e o [startAt] da partida (hora do servidor; converta com o [RoomClock]). [firstReadyAt] não está no banco:
 * é a hora do servidor (a `atividadeEm` da leitura) em que este aparelho viu o primeiro Pronto nesta música (ver
 * [RoomRound]). [boards] é o placar de cada rodada guardada no banco (a atual e a anterior), já na ordem (ver
 * [RoundBoard]).
 */
data class Room(
    val code: String,
    val owner: String,
    val players: List<RoomPlayer>,
    val me: String,
    val song: RoomSong? = null,
    val round: Int = 0,
    val state: RoomState = RoomState.LOBBY,
    val startAt: Long? = null,
    val firstReadyAt: Long? = null,
    val boards: Map<Int, List<BoardLine>> = emptyMap(),
) {
    val iAmOwner: Boolean get() = owner == me

    /** O placar da rodada atual, na ordem (ver [RoundBoard]). */
    val board: List<BoardLine> get() = boardOf(round)

    /** O placar da rodada [round] (vazio se o banco não tem mais: o dono apaga a de duas rodadas atrás). */
    fun boardOf(round: Int): List<BoardLine> = boards[round].orEmpty()

    /** A rodada atual acabou na hora do servidor [nowServer] (ver [RoundBoard.roundOver]). */
    fun roundOver(nowServer: Long): Boolean = RoundBoard.roundOver(this, nowServer)

    /** O Começar habilitado na hora do servidor [nowServer] (ver [RoomRound.canStart]). */
    fun canStart(nowServer: Long): Boolean = RoomRound.canStart(this, nowServer)

    /** Quem joga a rodada atual: vivos, prontos com a música e a dificuldade, e que não saíram dela. */
    fun playersInRound(): List<RoomPlayer> = RoomRound.playersInRound(this)
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
