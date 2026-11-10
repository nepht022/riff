package com.rodrigomachado.riff.core.room

/**
 * As regras da rodada (ver os Global Constraints do plano da M2), sobre a [Room] lida:
 * - Começar: o dono pronto com a música (é ele quem marca o começo: sem ele na rodada, a sala ficaria presa na
 *   contagem), pelo menos 2 vivos prontos com a música, e todos os vivos com a música prontos, ou 30 s depois do
 *   primeiro Pronto ("Começar com quem está pronto");
 * - começo: quando todos os da rodada carregaram, ou 15 s depois do Começar com quem carregou; a música começa 3 s
 *   depois, na hora do servidor.
 */
object RoomRound {
    const val MIN_PLAYERS = 2
    const val READY_WAIT_MS = 30_000L
    const val LOAD_WAIT_MS = 15_000L
    const val START_DELAY_MS = 3_000L

    /** A rodada órfã (ver `RoomRepository.heartbeat`): a folga depois do fim da música e depois do carregamento. */
    const val MATCH_GRACE_MS = 30_000L
    const val COUNTDOWN_GRACE_MS = 10_000L

    /** A duração suposta da música sem a `duracaoMs` (só para desfazer a partida órfã). */
    const val UNKNOWN_SONG_MS = 15 * 60_000L

    fun canStart(room: Room, nowServer: Long): Boolean {
        if (room.state != RoomState.LOBBY || room.song == null) return false
        val withSong = room.players.filter { !it.gone && it.song == SongStatus.HAS }
        val ready = withSong.filter(::isReady)
        if (ready.size < MIN_PLAYERS || ready.none { it.uid == room.owner }) return false
        val waited = room.firstReadyAt?.let { nowServer - it >= READY_WAIT_MS } ?: false
        return ready.size == withSong.size || waited
    }

    fun playersInRound(room: Room): List<RoomPlayer> =
        room.players.filter { !it.gone && isReady(it) && it.leftRound != room.round }

    /** Todos os da rodada [round] carregaram a música (ninguém na rodada também conta: não há quem esperar). */
    fun allLoaded(room: Room, round: Int): Boolean = playersInRound(room).all { it.loadedRound == round }

    /** Pronto para tocar: marcou Pronto, tem a música e escolheu a dificuldade. */
    private fun isReady(player: RoomPlayer) = player.ready && player.song == SongStatus.HAS && player.difficulty != null
}

/**
 * O primeiro Pronto visto nesta música, para o [Room.firstReadyAt]: a hora do servidor da leitura (a `atividadeEm`,
 * que o `setMe` grava junto com o Pronto) em que alguém vivo apareceu pronto. Volta a zero quando a música muda ou
 * ninguém mais está pronto. Quem abre a sala com alguém já pronto conta a partir de quando viu (nunca antes).
 */
internal class FirstReady {
    private var chartId: String? = null
    private var since: Long? = null

    @Synchronized
    fun update(room: Room, readAt: Long): Long? {
        val chart = room.song?.chartId
        val anyReady = room.players.any { !it.gone && it.ready && it.song == SongStatus.HAS }
        if (chart != chartId || !anyReady) since = null
        chartId = chart
        if (anyReady && chart != null && since == null) since = readAt
        return since
    }
}
