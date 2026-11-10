package com.rodrigomachado.riff.core.room

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.room.RoomFormat.ACTIVITY
import com.rodrigomachado.riff.core.room.RoomFormat.OWNER
import com.rodrigomachado.riff.core.room.RoomFormat.PLAYERS
import com.rodrigomachado.riff.core.room.RoomFormat.SEEN_AT
import com.rodrigomachado.riff.core.room.RoomFormat.SLOTS
import com.rodrigomachado.riff.core.room.RoundFormat.ROUND
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
 *
 * Na M2, a música (`musica`), a rodada (`rodada`, `estado`, `inicio`) e o estado de cada jogador para a música
 * (`dificuldade`, `pronto`, `musica`, `carregou`, `saiu`). As contas de tempo da rodada (os 30 s do Começar, os 15 s
 * do carregamento, o começo 3 s depois) usam a hora do servidor estimada pelo [measureClock].
 */
@Suppress("TooManyFunctions") // a sala inteira (M1 e M2) é uma API só, com uma função por ação mais as escritas
class RoomRepository(
    private val db: FirebaseDb,
    private val auth: AnonymousAuth,
    private val device: RoomDevice,
    private val clock: () -> Long,
    private val random: Random = Random.Default,
) {
    /** A sala em que este app entrou (criou ou entrou) e ainda não saiu: o PC sai dela ao fechar a janela. */
    @Volatile
    var joined: String? = null
        private set

    /** O último offset do relógio do servidor medido ([measureClock]); null = ainda não medido. */
    @Volatile
    private var offset: Long? = null
    private val firstReady = mutableMapOf<String, FirstReady>()
    private val countdowns = mutableMapOf<Pair<String, Int>, Long>()

    /**
     * Cria uma sala com um código sorteado: `PUT` só se o código está livre (`if-match: null_etag`). Código ocupado
     * por uma sala viva: sorteia outro (até 10 vezes); por uma sala morta: apaga a morta e fica com o código.
     */
    suspend fun create(name: String): CreateResult = try {
        val room = RoomFormat.newRoom(auth.uid(), RoomFormat.cleanName(name), device)
        firstFreeCode(room)?.let { code ->
            joined = code
            CreateResult.Created(code)
        } ?: CreateResult.NoConnection
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
        joinAs(auth.uid(), code, RoomFormat.cleanName(name)).also { if (it is JoinResult.Joined) joined = it.code }
    } catch (_: NoConnection) {
        JoinResult.NoConnection
    } catch (_: RoomHttpError) {
        JoinResult.NoConnection
    }

    /**
     * Sai da sala liberando a vaga. O dono passa o dono ao jogador mais antigo (vivo, se houver) na mesma escrita;
     * o último a sair apaga a sala. Sem internet não faz nada: quem saiu some em 60 s e o dono passa adiante. Esquece
     * o que este aparelho viu da sala (o primeiro Pronto, as contagens): outra sala pode nascer com o mesmo código.
     */
    suspend fun leave(code: String) {
        if (joined == code) joined = null
        forget(code)
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
     * A sala ao vivo (null = a sala acabou: não existe mais, ou eu não estou mais nela, tirado pelo dono ou porque
     * outra sala foi criada com o mesmo código). Emite a cada mudança no banco e recalcula o `gone`
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
                (tree as? JSONObject)?.let { read(code, it, me) }?.takeIf { room -> room.players.any { it.uid == me } }
            },
        )
    }.distinctUntilChanged()

    /**
     * Escolhe a música (só o dono, só na sala): ela, a minha dificuldade e eu com a música e não pronto. Os outros
     * conferem a música nova e gravam o próprio estado ([setMe]). O título, o artista e quem fez o mapa vão cortados
     * no limite das regras; um mapa sem dificuldades (que as regras recusam) nem é tentado. Devolve se gravou: false
     * sem dificuldades, sem internet ou recusado (a sala observada continua mostrando o que está no banco).
     */
    suspend fun chooseSong(code: String, song: RoomSong, myDifficulty: Difficulty): Boolean {
        if (song.difficulties.isEmpty()) return false
        var written = false
        quietly {
            db.patch(RoomFormat.path(code), RoundFormat.chooseSong(auth.uid(), song, myDifficulty))
            written = true
        }
        return written
    }

    /** O meu estado para a música da sala (null = sem dificuldade escolhida). Sem internet ou recusado, nada. */
    suspend fun setMe(code: String, difficulty: Difficulty?, ready: Boolean, song: SongStatus) = quietly {
        db.patch(RoomFormat.path(code), RoundFormat.setMe(auth.uid(), difficulty, ready, song))
    }

    /**
     * O Começar (só o dono, com o [Room.canStart] pela sala lida agora): a rodada seguinte, em contagem. Devolve se
     * gravou; false também sem internet ou recusado.
     */
    suspend fun startRound(code: String): Boolean = try {
        val path = RoomFormat.path(code)
        val json = db.get(path) as? JSONObject
        val room = json?.let { read(code, it, auth.uid()) }
        if (room == null || !room.iAmOwner) {
            false
        } else {
            val now = serverNow(code)
            room.canStart(now).also { can ->
                if (can) {
                    db.patch(path, RoundFormat.startRound(room.round + 1))
                    countdownSince(code, room.round + 1, now)
                }
            }
        }
    } catch (_: NoConnection) {
        false
    } catch (_: RoomHttpError) {
        false
    }

    /** A música da rodada [round] carregou (a partida montada, parada). */
    suspend fun markLoaded(code: String, round: Int) = markRound(code, round) { RoundFormat.loaded(it, round) }

    /** Saí da rodada [round] no meio (não conto mais para ela). */
    suspend fun markLeft(code: String, round: Int) = markRound(code, round) { RoundFormat.left(it, round) }

    /**
     * O dono marca o começo da rodada [round] (ainda em contagem) quando todos os que jogam carregaram, ou 15 s
     * depois do Começar (contados de quando este aparelho viu a contagem: o dono novo conta de quando assumiu):
     * `partida` com o `inicio` 3 s depois, na hora do servidor. Para ser chamado de tempos em tempos durante a
     * contagem; fora dela, de outra rodada, ou sem ser o dono, não faz nada.
     */
    suspend fun scheduleStartIfDue(code: String, round: Int) = quietly {
        val path = RoomFormat.path(code)
        val json = db.get(path) as? JSONObject ?: return@quietly
        val room = read(code, json, auth.uid())
        if (!room.iAmOwner || room.round != round || room.state != RoomState.COUNTDOWN) return@quietly
        val now = serverNow(code)
        val since = countdownSince(code, round, now)
        if (RoomRound.allLoaded(room, round) || now - since >= RoomRound.LOAD_WAIT_MS) {
            db.patch(path, RoundFormat.start(now + RoomRound.START_DELAY_MS))
        }
    }

    /** De volta à sala (o dono, ao sair da partida): sem começo marcado. Sem internet ou recusado, nada. */
    suspend fun backToLobby(code: String) = quietly { db.patch(RoomFormat.path(code), RoundFormat.backToLobby()) }

    /**
     * Mede a diferença entre o relógio do servidor e o deste aparelho: 3 vezes grava o `vistoEm` (a hora do
     * servidor) e lê de volta; `offset = vistoEm − (t0 + t1) / 2`, ficando com a ida e volta mais rápida (a de menor
     * erro). Guarda o offset para as contas de tempo da rodada. Sem nenhuma medida (sem internet, recusado), lança
     * [NoConnection].
     */
    suspend fun measureClock(code: String): RoomClock = RoomClock(measureOffset(code), clock)

    private suspend fun measureOffset(code: String): Long {
        val me = try {
            auth.uid()
        } catch (e: RoomHttpError) {
            throw NoConnection(e)
        }
        val path = RoomFormat.path(code)
        var best: Pair<Long, Long>? = null // ida e volta, offset
        repeat(CLOCK_TRIES) {
            val t0 = clock()
            val seen = try {
                db.patch(path, mapOf("$PLAYERS/$me/$SEEN_AT" to ServerTime.NOW, ACTIVITY to ServerTime.NOW))
                db.get("$path/$PLAYERS/$me/$SEEN_AT") as? Number
            } catch (_: NoConnection) {
                null // fica com as outras medidas
            } catch (_: RoomHttpError) {
                null
            }
            val t1 = clock()
            if (seen != null && (best == null || t1 - t0 < best.first)) {
                best = t1 - t0 to seen.toLong() - (t0 + t1) / 2
            }
        }
        return (best?.second ?: throw NoConnection()).also { offset = it }
    }

    /** A hora do servidor agora, pelo último offset medido (mede se ainda não mediu). */
    private suspend fun serverNow(code: String): Long = clock() + (offset ?: measureOffset(code))

    private fun forget(code: String) {
        synchronized(firstReady) { firstReady.remove(code) }
        synchronized(countdowns) { countdowns.keys.removeAll { it.first == code } }
    }

    /** Quando este aparelho viu a contagem da rodada [round] pela primeira vez (hora do servidor). */
    private fun countdownSince(code: String, round: Int, now: Long): Long =
        synchronized(countdowns) { countdowns.getOrPut(code to round) { now } }

    /** A sala lida, com o primeiro Pronto que este aparelho viu nesta música. */
    private fun read(code: String, json: JSONObject, me: String): Room {
        val room = RoomFormat.room(code, json, me, clock())
        val tracker = synchronized(firstReady) { firstReady.getOrPut(code) { FirstReady() } }
        return room.copy(firstReadyAt = tracker.update(room, json.optLong(ACTIVITY)))
    }

    /**
     * Grava o `carregou`/`saiu` da rodada [round]. Recusado: relê; se a rodada ainda é essa, tenta de novo; se a sala
     * já está noutra rodada, a escrita não vale mais e fica por isso.
     */
    private suspend fun markRound(code: String, round: Int, write: (String) -> Map<String, Any?>) = quietly {
        val path = RoomFormat.path(code)
        val values = write(auth.uid())
        repeat(MARK_TRIES) {
            try {
                db.patch(path, values)
                return@quietly
            } catch (e: RoomHttpError) {
                if (e.code !in REFUSED) throw e
                val current = (db.get(path) as? JSONObject)?.optInt(ROUND, 0)
                if (current != round) return@quietly
            }
        }
    }

    /** Uma escrita que, sem internet ou recusada, não faz nada: a sala observada mostra o que está no banco. */
    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (_: NoConnection) {
            // sem internet: a tela continua com a sala como está
        } catch (_: RoomHttpError) {
            // recusado (não sou mais o dono, a sala mudou): idem
        }
    }

    private suspend fun firstFreeCode(room: Map<String, Any?>): String? {
        repeat(CREATE_TRIES) {
            val code = RoomCodes.random(random)
            if (claim(code, room)) return code
        }
        return null
    }

    /**
     * Grava a sala nova em [code] se o código está livre ou se a sala que está nele morreu. O `PUT` condicional
     * recusado (412, ou 401/403: ver [FirebaseDb.put]) é código ocupado: relê para decidir.
     */
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
        // outro pode ter criado entre o apagar e o PUT; código livre e recusado de novo: as regras (outro código)
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
        const val CLOCK_TRIES = 3
        const val MARK_TRIES = 2
        const val RECHECK_MS = 5_000L
        const val VANISHED_MS = 5 * 60_000L
        val REFUSED = setOf(401, 403)
    }
}
