package com.rodrigomachado.riff.core.room

/** Um placar lido do banco: o que o jogador mandou e quando chegou ([updatedAt], hora do servidor). */
internal data class WrittenScore(val score: RoundScore, val updatedAt: Long)

/**
 * O placar da rodada (ver os Global Constraints e a Decisão 3 do plano da M3):
 * - quem está nele: quem tem placar na rodada, mais quem carregou a música dela (ainda sem placar);
 * - a ordem: pontos (desc), depois o maior combo (desc), depois quem entrou primeiro na sala (e o uid), para dois
 *   empatados não trocarem de lugar a cada leitura; quem saiu da rodada ou da sala, sempre por último (na mesma
 *   ordem entre eles), menos quem terminou antes de sair, que fica na posição (e no pódio);
 * - "sem conexão" ([BoardLine.stale]): sem terminar e sem placar novo há [STALE_MS], ou sem nenhum [STALE_MS] depois
 *   do começo (numa rodada que já passou, quem nunca mandou);
 * - o fim da rodada: todos terminaram ou saíram, ou [END_GRACE_MS] depois do fim da música.
 */
object RoundBoard {
    const val STALE_MS = 15_000L
    const val END_GRACE_MS = 10_000L

    /**
     * As linhas da rodada [round] da [room] com os [scores] lidos, na hora do servidor [nowServer]. [known] são os
     * jogadores que este aparelho já viu na sala (o nome e o aparelho de quem saiu dela).
     */
    internal fun lines(
        room: Room,
        round: Int,
        scores: Map<String, WrittenScore>,
        nowServer: Long,
        known: Map<String, RoomPlayer> = emptyMap(),
    ): List<BoardLine> {
        val inRoom = room.players.associateBy { it.uid }
        val loaded = room.players.filter { it.loadedRound == round }.map { it.uid }
        val uids = (scores.keys + loaded).distinct()
        val order = room.players.map { it.uid }
        return uids.map { uid ->
            val player = inRoom[uid]
            val seen = player ?: known[uid]
            val written = scores[uid]
            val score = written?.score
            val finished = score?.finished ?: false
            // quem terminou e depois saiu (da rodada ou da sala) fica na posição que ganhou, sem o "saiu"
            val left = !finished && (player == null || player.leftRound == round)
            BoardLine(
                uid = uid,
                name = seen?.name.orEmpty(),
                device = seen?.device ?: RoomDevice.PHONE,
                score = score?.score ?: 0,
                combo = score?.combo ?: 0,
                maxCombo = score?.maxCombo ?: 0,
                accuracy = score?.accuracy ?: 0f,
                finished = finished,
                left = left,
                stale = !finished && !left && silent(room, round, written, nowServer),
            )
        }.sortedWith(
            compareBy<BoardLine> { it.left }
                .thenByDescending { it.score }
                .thenByDescending { it.maxCombo }
                .thenBy { order.indexOf(it.uid).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
                .thenBy { it.uid },
        )
    }

    /**
     * A rodada atual acabou: todos os do placar terminaram ou saíram (com alguém nele), ou passaram [END_GRACE_MS]
     * do fim da música ([RoomSong.durationMs]; sem ela, [RoomRound.UNKNOWN_SONG_MS]) contado do começo.
     */
    fun roundOver(room: Room, nowServer: Long): Boolean {
        val board = room.board
        if (board.isNotEmpty() && board.all { it.finished || it.left }) return true
        val startAt = room.startAt ?: return false
        val songMs = room.song?.durationMs ?: RoomRound.UNKNOWN_SONG_MS
        return nowServer >= startAt + songMs + END_GRACE_MS
    }

    /** Sem placar novo há [STALE_MS]: pelo último recebido, ou pelo começo de quem nunca mandou. */
    private fun silent(room: Room, round: Int, written: WrittenScore?, nowServer: Long): Boolean {
        if (written != null) return nowServer - written.updatedAt >= STALE_MS
        if (round < room.round) return true
        val startAt = room.startAt?.takeIf { round == room.round } ?: return false
        return nowServer - startAt >= STALE_MS
    }
}
