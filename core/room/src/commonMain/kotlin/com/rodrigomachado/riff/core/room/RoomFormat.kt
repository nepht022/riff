package com.rodrigomachado.riff.core.room

import org.json.JSONObject

/**
 * O formato da sala no banco (o que as regras de `firebase/database.rules.json` aceitam): os nomes dos campos, as
 * escritas montadas e a leitura da sala para o [Room].
 */
internal object RoomFormat {
    const val OWNER = "dono"
    const val ACTIVITY = "atividadeEm"
    const val PLAYERS = "jogadores"
    const val SLOTS = "vagas"
    const val SEEN_AT = "vistoEm"
    private const val STATE = "estado"
    private const val CREATED = "criadaEm"
    private const val NAME = "nome"
    private const val DEVICE = "aparelho"
    private const val SLOT = "vaga"
    private const val JOINED_AT = "entrouEm"
    private const val MAX_NAME = 20
    private const val STALE_MS = 2 * 60 * 60_000L
    private const val GONE_MS = 60_000L
    private val SLOT_NAMES = listOf("v1", "v2", "v3", "v4")

    fun path(code: String) = "salas/$code"

    /** O nome como vai no banco: sem espaços nas pontas e até 20 caracteres (o do ranking). */
    fun cleanName(name: String) = name.trim().take(MAX_NAME)

    /** A sala nova: o dono como o único jogador, na v1, com os tempos do servidor. */
    fun newRoom(me: String, name: String, device: RoomDevice): Map<String, Any?> = mapOf(
        OWNER to me,
        STATE to "sala",
        CREATED to ServerTime.NOW,
        ACTIVITY to ServerTime.NOW,
        PLAYERS to mapOf(me to player(name, device, SLOT_NAMES.first(), ServerTime.NOW)),
        SLOTS to mapOf(SLOT_NAMES.first() to me),
    )

    /**
     * Entrar na [slot] numa escrita só: o jogador, a vaga e a atividade. Quem já estava na sala mantém o `entrouEm`
     * (as regras aceitam regravar o mesmo valor).
     */
    fun join(room: JSONObject, me: String, name: String, device: RoomDevice, slot: String): Map<String, Any?> {
        val joinedAt = room.optJSONObject(PLAYERS)?.optJSONObject(me)?.opt(JOINED_AT) as? Number
        return mapOf(
            "$PLAYERS/$me" to player(name, device, slot, joinedAt ?: ServerTime.NOW),
            "$SLOTS/$slot" to me,
            ACTIVITY to ServerTime.NOW,
        )
    }

    private fun player(name: String, device: RoomDevice, slot: String, joinedAt: Any) = mapOf(
        NAME to name,
        DEVICE to device.wire,
        SLOT to slot,
        JOINED_AT to joinedAt,
        SEEN_AT to ServerTime.NOW,
    )

    /** Morta: sem atividade há mais de 2 h pelo relógio [now] (as regras conferem com o do servidor). */
    fun isStale(room: JSONObject, now: Long) = room.optLong(ACTIVITY, now) < now - STALE_MS

    /** O tempo (do servidor) mais novo gravado na sala: a `atividadeEm` ou o `vistoEm` de algum jogador. */
    fun latestTime(room: JSONObject): Long {
        val players = room.optJSONObject(PLAYERS)
        val seen = players?.keys()?.asSequence()?.map { players.optJSONObject(it)?.optLong(SEEN_AT) ?: 0L }
        return maxOf(room.optLong(ACTIVITY), seen?.maxOrNull() ?: 0L)
    }

    /**
     * A minha vaga, se ainda é minha; senão a primeira livre: vazia ou abandonada (o uid nela não aponta mais para
     * ela, como nas regras). Null = sala cheia.
     */
    fun freeSlot(room: JSONObject, me: String): String? {
        mySlot(room, me)?.let { return it }
        val slots = room.optJSONObject(SLOTS)
        val players = room.optJSONObject(PLAYERS)
        return SLOT_NAMES.firstOrNull { slot ->
            val holder = slots?.optString(slot).orEmpty()
            holder.isEmpty() || players?.optJSONObject(holder)?.optString(SLOT) != slot
        }
    }

    /** A vaga para onde eu aponto, se ela guarda o meu uid. */
    fun mySlot(room: JSONObject, me: String): String? {
        val slot = room.optJSONObject(PLAYERS)?.optJSONObject(me)?.optString(SLOT).orEmpty()
        return slot.takeIf { it.isNotEmpty() && room.optJSONObject(SLOTS)?.optString(it) == me }
    }

    fun room(code: String, room: JSONObject, me: String, now: Long) =
        Room(code, room.optString(OWNER), players(room, now), me)

    /** Os jogadores na ordem em que entraram (o `uid` desempata), com o `gone` pelo relógio [now]. */
    fun players(room: JSONObject, now: Long): List<RoomPlayer> {
        val list = room.optJSONObject(PLAYERS) ?: return emptyList()
        return list.keys().asSequence().mapNotNull { uid ->
            list.optJSONObject(uid)?.let { player ->
                val seenAt = player.optLong(SEEN_AT)
                RoomPlayer(
                    uid = uid,
                    name = player.optString(NAME),
                    device = RoomDevice.of(player.optString(DEVICE)),
                    joinedAt = player.optLong(JOINED_AT),
                    seenAt = seenAt,
                    gone = now - seenAt > GONE_MS,
                )
            }
        }.sortedWith(compareBy({ it.joinedAt }, { it.uid })).toList()
    }
}
