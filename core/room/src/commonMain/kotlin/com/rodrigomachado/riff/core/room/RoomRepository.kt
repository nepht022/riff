package com.rodrigomachado.riff.core.room

import com.rodrigomachado.riff.core.room.RoomFormat.ACTIVITY
import com.rodrigomachado.riff.core.room.RoomFormat.OWNER
import com.rodrigomachado.riff.core.room.RoomFormat.PLAYERS
import com.rodrigomachado.riff.core.room.RoomFormat.SEEN_AT
import com.rodrigomachado.riff.core.room.RoomFormat.SLOTS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import kotlin.random.Random

/**
 * A sala do multiplayer no Realtime Database: `salas/{CODIGO}` com `dono`, `estado`, `criadaEm`, `atividadeEm`,
 * `jogadores/{uid}: {nome, aparelho, vaga, entrouEm, vistoEm}` e `vagas/{v1..v4}: uid`. Cada escrita tem o formato
 * que as regras (`firebase/database.rules.json`) aceitam, o mesmo das escritas de `firebase/rules.test.mjs`; os
 * tempos são sempre os do servidor ([ServerTime.NOW]).
 *
 * O [clock] (do aparelho) só decide o que o app tenta: sala morta (2 h sem atividade) e jogador que sumiu (60 s sem
 * escrever). Quem confere de verdade são as regras, com a hora do servidor; se o relógio do aparelho errar, a escrita
 * é recusada e o app segue (outro código, o dono fica como está).
 */
class RoomRepository(
    private val db: FirebaseDb,
    private val auth: AnonymousAuth,
    private val device: RoomDevice,
    private val clock: () -> Long,
    private val random: Random = Random.Default,
) {
    /**
     * Cria uma sala com um código sorteado: `PUT` só se o código está livre (`if-match: null_etag`). Código ocupado
     * por uma sala viva: sorteia outro (até 10 vezes); por uma sala morta: apaga a morta e fica com o código.
     */
    suspend fun create(name: String): CreateResult = try {
        val room = RoomFormat.newRoom(auth.uid(), RoomFormat.cleanName(name), device)
        firstFreeCode(room)?.let { CreateResult.Created(it) } ?: CreateResult.NoConnection
    } catch (_: NoConnection) {
        CreateResult.NoConnection
    } catch (_: RoomHttpError) {
        CreateResult.NoConnection // as regras recusaram ou o Firebase falhou: a tela pede para tentar de novo
    }

    /**
     * Entra na sala [code] (já normalizado, ver [RoomCodes.normalize]) ocupando uma vaga livre, numa escrita só (o
     * jogador, a vaga e a atividade). Recusado (outro ocupou a vaga antes): relê e tenta outra. Entrar de novo
     * mantém o `entrouEm` e a vaga.
     */
    suspend fun join(code: String, name: String): JoinResult = try {
        joinAs(auth.uid(), code, RoomFormat.cleanName(name))
    } catch (_: NoConnection) {
        JoinResult.NoConnection
    } catch (_: RoomHttpError) {
        JoinResult.NoConnection
    }

    /**
     * Sai da sala liberando a vaga. O dono passa o dono ao jogador mais antigo (vivo, se houver) na mesma escrita;
     * o último a sair apaga a sala. Sem internet não faz nada: quem saiu some em 60 s e o dono passa adiante.
     */
    suspend fun leave(code: String) {
        try {
            leaveAs(auth.uid(), code)
        } catch (_: NoConnection) {
            // sem internet: ver acima
        } catch (_: RoomHttpError) {
            // recusado (a sala mudou no meio): idem
        }
    }

    /**
     * O batimento (a cada 20 s, enquanto a sala está aberta): grava o `vistoEm` e a `atividadeEm`. Se o dono
     * sumiu (60 s sem escrever, ou fora da sala) e eu sou o jogador vivo mais antigo, fico com o dono. Sendo o dono,
     * tiro da sala quem sumiu há mais de 5 min (libera a vaga). Fora da sala, sem internet ou recusado, não faz nada.
     */
    suspend fun heartbeat(code: String) {
        try {
            beat(auth.uid(), code)
        } catch (_: NoConnection) {
            // o próximo batimento tenta de novo
        } catch (_: RoomHttpError) {
            // outro jogador assumiu o dono antes, ou saí da sala
        }
    }

    /**
     * A sala ao vivo (null = não existe mais: a sala acabou). Emite a cada mudança no banco e recalcula o `gone`
     * dos jogadores a cada 5 s mesmo sem mudança. O streaming reabre sozinho quando cai (ver [FirebaseDb.observe]);
     * sem internet para o login ou com o login recusado (o Google fora do ar, uma resposta estranha), lança
     * [NoConnection].
     */
    fun observe(code: String): Flow<Room?> = flow {
        val me = try {
            auth.uid()
        } catch (e: RoomHttpError) {
            throw NoConnection(e)
        }
        val ticks = flow {
            while (true) {
                emit(Unit)
                delay(RECHECK_MS)
            }
        }
        val trees = db.observe(RoomFormat.path(code))
        emitAll(
            combine(trees, ticks) { tree, _ ->
                (tree as? JSONObject)?.let { RoomFormat.room(code, it, me, clock()) }
            },
        )
    }.distinctUntilChanged()

    private suspend fun firstFreeCode(room: Map<String, Any?>): String? {
        repeat(CREATE_TRIES) {
            val code = RoomCodes.random(random)
            if (claim(code, room)) return code
        }
        return null
    }

    /** Grava a sala nova em [code] se o código está livre ou se a sala que está nele morreu. */
    private suspend fun claim(code: String, room: Map<String, Any?>): Boolean {
        val path = RoomFormat.path(code)
        if (db.put(path, room, ifMatch = NULL_ETAG) == PutResult.Ok) return true
        val existing = db.get(path) as? JSONObject
        if (existing != null) {
            if (!RoomFormat.isStale(existing, clock())) return false
            try {
                db.delete(path)
            } catch (e: RoomHttpError) {
                if (e.code !in REFUSED) throw e
                return false // para o servidor a sala ainda está viva
            }
        }
        return db.put(path, room, ifMatch = NULL_ETAG) == PutResult.Ok
    }

    private suspend fun joinAs(me: String, code: String, name: String): JoinResult {
        val path = RoomFormat.path(code)
        repeat(JOIN_TRIES) {
            val room = db.get(path) as? JSONObject ?: return JoinResult.NotFound
            val slot = RoomFormat.freeSlot(room, me) ?: return JoinResult.Full
            try {
                db.patch(path, RoomFormat.join(room, me, name, device, slot))
                return JoinResult.Joined(code)
            } catch (e: RoomHttpError) {
                if (e.code !in REFUSED) throw e
            }
        }
        return JoinResult.NoConnection // recusado sempre, com vaga livre: algo errado no servidor
    }

    private suspend fun leaveAs(me: String, code: String) {
        val path = RoomFormat.path(code)
        val room = db.get(path) as? JSONObject ?: return
        val players = RoomFormat.players(room, clock())
        if (players.none { it.uid == me }) return
        val others = players.filter { it.uid != me }
        val owner = room.optString(OWNER)
        if (others.isEmpty()) {
            // o último apaga a sala; se o dono já não estava nela, fico com o dono antes (só o dono apaga)
            if (owner != me) db.patch(path, mapOf(OWNER to me))
            db.delete(path)
            return
        }
        val write = mutableMapOf<String, Any?>("$PLAYERS/$me" to null)
        RoomFormat.mySlot(room, me)?.let { write["$SLOTS/$it"] = null }
        if (owner == me) write[OWNER] = others.firstOrNull { !it.gone }?.uid ?: others.first().uid
        db.patch(path, write)
    }

    private suspend fun beat(me: String, code: String) {
        val path = RoomFormat.path(code)
        val room = db.get(path) as? JSONObject ?: return
        val players = RoomFormat.players(room, clock())
        // fora da sala não grava nada (sem as regras, um vistoEm sozinho criaria um jogador pela metade)
        if (players.none { it.uid == me }) return
        db.patch(path, mapOf("$PLAYERS/$me/$SEEN_AT" to ServerTime.NOW, ACTIVITY to ServerTime.NOW))
        val owner = room.optString(OWNER)
        val ownerGone = players.firstOrNull { it.uid == owner }?.gone ?: true
        // eu acabei de escrever: conto como vivo mesmo que a leitura seja de antes
        val heir = players.firstOrNull { it.uid != owner && (it.uid == me || !it.gone) }
        if (ownerGone && heir?.uid == me) db.patch(path, mapOf(OWNER to me))
        if (owner == me) removeVanished(path, room, players, me)
    }

    /**
     * Tira os jogadores sem escrever há mais de 5 min (e as vagas que ainda são deles), numa escrita só. O "agora" é
     * a hora do servidor estimada pelo tempo mais novo gravado na sala (o último batimento de alguém, há no máximo
     * ~20 s): fica para trás, nunca adiantado, então ninguém sai antes dos 5 min, mesmo com o relógio do aparelho
     * errado (as regras deixam o dono remover qualquer jogador).
     */
    private suspend fun removeVanished(path: String, room: JSONObject, players: List<RoomPlayer>, me: String) {
        val serverNow = RoomFormat.latestTime(room)
        val write = mutableMapOf<String, Any?>()
        players.filter { it.uid != me && serverNow - it.seenAt > VANISHED_MS }.forEach { player ->
            write["$PLAYERS/${player.uid}"] = null
            RoomFormat.mySlot(room, player.uid)?.let { write["$SLOTS/$it"] = null }
        }
        if (write.isNotEmpty()) db.patch(path, write)
    }

    private companion object {
        const val NULL_ETAG = "null_etag"
        const val CREATE_TRIES = 10
        const val JOIN_TRIES = 5
        const val RECHECK_MS = 5_000L
        const val VANISHED_MS = 5 * 60_000L
        val REFUSED = setOf(401, 403)
    }
}
