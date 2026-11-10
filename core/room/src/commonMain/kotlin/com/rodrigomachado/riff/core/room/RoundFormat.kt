package com.rodrigomachado.riff.core.room

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.room.RoomFormat.ACTIVITY
import com.rodrigomachado.riff.core.room.RoomFormat.PLAYERS
import org.json.JSONObject

/**
 * O formato da M2 no banco (o que `firebase/database.rules.json` aceita; as mesmas escritas dos testes
 * "repository…" de `firebase/rules.test.mjs`): a música, a rodada, o começo e o estado de cada jogador para a música.
 */
internal object RoundFormat {
    const val STATE = "estado"
    const val ROUND = "rodada"
    const val START = "inicio"
    const val SONG = "musica"
    const val DIFFICULTY = "dificuldade"
    const val READY = "pronto"
    const val LOADED = "carregou"
    const val LEFT = "saiu"
    private const val CHART_ID = "chartId"
    private const val TITLE = "titulo"
    private const val ARTIST = "artista"
    private const val DIFFICULTIES = "dificuldades"
    private const val DURATION = "duracaoMs"
    private const val ENCORE = "encore"
    private const val MD5 = "md5"
    private const val VIDEO = "video"
    private const val CHARTER = "charter"

    /** Escolher a música (o dono): ela, a minha dificuldade, "tem" e não pronto, e a atividade. */
    fun chooseSong(me: String, song: RoomSong, difficulty: Difficulty): Map<String, Any?> = mapOf(
        SONG to songMap(song),
        "$PLAYERS/$me/$DIFFICULTY" to difficulty.name,
        "$PLAYERS/$me/$SONG" to SongStatus.HAS.wire,
        "$PLAYERS/$me/$READY" to false,
        ACTIVITY to ServerTime.NOW,
    )

    /** O meu estado para a música: dificuldade (null apaga), Pronto, se tenho a música, e a atividade. */
    fun setMe(me: String, difficulty: Difficulty?, ready: Boolean, song: SongStatus): Map<String, Any?> = mapOf(
        "$PLAYERS/$me/$DIFFICULTY" to difficulty?.name,
        "$PLAYERS/$me/$READY" to ready,
        "$PLAYERS/$me/$SONG" to song.wire,
        ACTIVITY to ServerTime.NOW,
    )

    /** O Começar (o dono): contagem da rodada [round] (a seguinte), sem começo marcado. */
    fun startRound(round: Int): Map<String, Any?> =
        mapOf(STATE to RoomState.COUNTDOWN.wire, ROUND to round, START to null, ACTIVITY to ServerTime.NOW)

    /** O começo da partida (o dono), na hora do servidor [startAt]. */
    fun start(startAt: Long): Map<String, Any?> =
        mapOf(STATE to RoomState.PLAYING.wire, START to startAt, ACTIVITY to ServerTime.NOW)

    /** De volta à sala (o dono): sem começo marcado. */
    fun backToLobby(): Map<String, Any?> =
        mapOf(STATE to RoomState.LOBBY.wire, START to null, ACTIVITY to ServerTime.NOW)

    /** A rodada [round] em que a minha música carregou. */
    fun loaded(me: String, round: Int): Map<String, Any?> = mapOf("$PLAYERS/$me/$LOADED" to round)

    /** A rodada [round] que eu abandonei. */
    fun left(me: String, round: Int): Map<String, Any?> = mapOf("$PLAYERS/$me/$LEFT" to round)

    /** A `musica` da sala lida (as dificuldades desconhecidas ficam de fora). */
    fun song(json: JSONObject) = RoomSong(
        chartId = json.optString(CHART_ID),
        title = json.optString(TITLE),
        artist = json.optString(ARTIST),
        difficulties = json.optString(DIFFICULTIES).split(',').mapNotNull(::difficulty).toSet(),
        durationMs = (json.opt(DURATION) as? Number)?.toLong(),
        encore = json.optJSONObject(ENCORE)?.let { encore ->
            EncoreRef(
                md5 = encore.optString(MD5),
                video = encore.optBoolean(VIDEO),
                charter = encore.opt(CHARTER) as? String,
            )
        },
    )

    fun difficulty(name: String?): Difficulty? = Difficulty.entries.firstOrNull { it.name == name }

    /** A música como vai no banco: as dificuldades na ordem do jogo, sem os opcionais ausentes. */
    private fun songMap(song: RoomSong): Map<String, Any?> = buildMap {
        put(CHART_ID, song.chartId)
        put(TITLE, song.title)
        put(ARTIST, song.artist)
        put(DIFFICULTIES, song.difficulties.sorted().joinToString(",") { it.name })
        song.durationMs?.let { put(DURATION, it) }
        song.encore?.let { encore ->
            val ref = mutableMapOf<String, Any?>(MD5 to encore.md5, VIDEO to encore.video)
            encore.charter?.let { ref[CHARTER] = it }
            put(ENCORE, ref)
        }
    }
}
