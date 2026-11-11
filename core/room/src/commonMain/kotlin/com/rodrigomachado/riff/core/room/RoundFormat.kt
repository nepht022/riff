package com.rodrigomachado.riff.core.room

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.room.RoomFormat.ACTIVITY
import com.rodrigomachado.riff.core.room.RoomFormat.PLAYERS
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToLong

/**
 * O formato da M2 no banco (o que `firebase/database.rules.json` aceita; as mesmas escritas dos testes
 * "repository…" de `firebase/rules.test.mjs`): a música, a rodada, o começo e o estado de cada jogador para a música.
 * O placar da M3 está no [ScoreFormat].
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

    /** O limite das regras para `titulo`, `artista` e `charter`. */
    private const val MAX_TEXT = 200

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

    /** O meu Pronto de volta a "não" (sobrou de uma rodada ou música anterior), e a atividade. */
    fun clearReady(me: String): Map<String, Any?> = mapOf("$PLAYERS/$me/$READY" to false, ACTIVITY to ServerTime.NOW)

    /**
     * O Começar (o dono): contagem da rodada [round] (a seguinte), sem começo marcado, apagando o placar da rodada
     * [round] − 2 (a anterior fica, para o Resultado da sala de quem atrasou); nas rodadas 1 e 2 não há o que apagar.
     */
    fun startRound(round: Int): Map<String, Any?> = buildMap {
        put(STATE, RoomState.COUNTDOWN.wire)
        put(ROUND, round)
        put(START, null)
        put(ACTIVITY, ServerTime.NOW)
        if (round > 2) put("${ScoreFormat.BOARD}/${round - 2}", null)
    }

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

    /**
     * A música como vai no banco: as dificuldades na ordem do jogo, sem os opcionais ausentes, e os textos cortados
     * no limite das regras ([MAX_TEXT]).
     */
    private fun songMap(song: RoomSong): Map<String, Any?> = buildMap {
        put(CHART_ID, song.chartId)
        put(TITLE, song.title.take(MAX_TEXT))
        put(ARTIST, song.artist.take(MAX_TEXT))
        put(DIFFICULTIES, song.difficulties.sorted().joinToString(",") { it.name })
        song.durationMs?.let { put(DURATION, it) }
        song.encore?.let { encore ->
            val ref = mutableMapOf<String, Any?>(MD5 to encore.md5, VIDEO to encore.video)
            encore.charter?.let { ref[CHARTER] = it.take(MAX_TEXT) }
            put(ENCORE, ref)
        }
    }
}

/**
 * O formato do placar da M3 no banco (`placar/{rodada}/{uid}`; o que as regras aceitam, as mesmas escritas dos testes
 * "repository…" de `firebase/rules.test.mjs`).
 */
internal object ScoreFormat {
    const val BOARD = "placar"
    private const val POINTS = "pontos"
    private const val COMBO = "combo"
    private const val MAX_COMBO = "maiorCombo"
    private const val HITS = "acertos"
    private const val NOTES = "notas"
    private const val ACCURACY = "precisao"
    private const val FINISHED = "terminou"
    private const val UPDATED_AT = "atualizadoEm"

    /** Os tetos das regras para o placar; a `precisao` vai em centésimos de % (10000 = 100%). */
    private const val MAX_POINTS = 1_000_000_000L
    private const val MAX_COUNT = 1_000_000L
    private const val ACCURACY_SCALE = 10_000f

    /** O caminho do meu placar na rodada [round], dentro da sala. */
    fun scorePath(me: String, round: Int) = "$BOARD/$round/$me"

    /** O meu placar inteiro como vai no `PUT` (nos limites das regras), com a hora do servidor. */
    fun score(score: RoundScore): Map<String, Any?> = mapOf(
        POINTS to score.score.coerceIn(0, MAX_POINTS),
        COMBO to count(score.combo),
        MAX_COMBO to count(score.maxCombo),
        HITS to count(score.hits),
        NOTES to count(score.notes),
        ACCURACY to (score.accuracy.coerceIn(0f, 1f) * ACCURACY_SCALE).roundToLong(),
        FINISHED to score.finished,
        UPDATED_AT to ServerTime.NOW,
    )

    /**
     * O `placar` da sala lido: rodada → uid → placar. O Firebase devolve as rodadas como lista (`[null, {…}, …]`)
     * quando as chaves são números seguidos; as duas formas valem. Campos ausentes contam como 0.
     */
    fun scores(room: JSONObject): Map<Int, Map<String, WrittenScore>> =
        children(room.opt(BOARD)).mapNotNull { (key, round) ->
            val number = key.toIntOrNull() ?: return@mapNotNull null
            number to children(round).associate { (uid, json) -> uid to writtenScore(json) }
        }.toMap()

    /** O tempo (do servidor) mais novo de um placar gravado na sala (0 sem nenhum). */
    fun latestScoreTime(scores: Map<Int, Map<String, WrittenScore>>): Long =
        scores.values.flatMap { it.values }.maxOfOrNull { it.updatedAt } ?: 0L

    private fun writtenScore(json: JSONObject) = WrittenScore(
        RoundScore(
            score = json.optLong(POINTS),
            combo = json.optInt(COMBO),
            maxCombo = json.optInt(MAX_COMBO),
            hits = json.optInt(HITS),
            notes = json.optInt(NOTES),
            accuracy = json.optLong(ACCURACY) / ACCURACY_SCALE,
            finished = json.optBoolean(FINISHED),
        ),
        updatedAt = json.optLong(UPDATED_AT),
    )

    /** Os filhos objeto de um nó (objeto ou lista, como o Firebase manda), com a chave. */
    private fun children(node: Any?): List<Pair<String, JSONObject>> = when (node) {
        is JSONObject -> node.keys().asSequence().mapNotNull { key -> node.optJSONObject(key)?.let { key to it } }
            .toList()

        is JSONArray -> (0 until node.length()).mapNotNull { i -> node.optJSONObject(i)?.let { i.toString() to it } }

        else -> emptyList()
    }

    private fun count(value: Int) = value.toLong().coerceIn(0, MAX_COUNT)
}
