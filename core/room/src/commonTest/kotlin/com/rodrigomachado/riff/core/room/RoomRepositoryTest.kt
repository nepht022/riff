package com.rodrigomachado.riff.core.room

import com.rodrigomachado.riff.core.room.testing.CodesRandom
import com.rodrigomachado.riff.core.room.testing.FakeFirebase
import com.rodrigomachado.riff.core.room.testing.FakePlayer
import com.rodrigomachado.riff.core.room.testing.FirebaseWrite
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class) // o relógio virtual do runTest
class RoomRepositoryTest : JsonTest() {
    private var now = T0
    private val fake = FakeFirebase(clock = { now }, uid = "u1")

    private fun room(code: String = "ABCD") = fake.get("salas/$code") as JSONObject?

    private fun players(code: String = "ABCD") = room(code)!!.getJSONObject("jogadores")

    private fun keys(json: JSONObject) = json.keys().asSequence().toSet()

    /** As chaves do corpo de um PATCH (os caminhos que ele grava). */
    private fun patchKeys(write: FirebaseWrite) = keys(JSONObject(write.body!!))

    // --- criar ---

    @Test
    fun createWritesOwnerAndFirstPlayer() = runTest {
        val repository = fake.repository(random = CodesRandom("ABCD"))

        assertEquals(CreateResult.Created("ABCD"), repository.create("Ana"))

        val room = room()!!
        assertEquals("u1", room.getString("dono"))
        assertEquals("sala", room.getString("estado"))
        assertEquals(now, room.getLong("criadaEm"))
        assertEquals(now, room.getLong("atividadeEm"))
        assertEquals(setOf("u1"), keys(room.getJSONObject("jogadores")))
        val me = room.getJSONObject("jogadores").getJSONObject("u1")
        assertEquals("Ana", me.getString("nome"))
        assertEquals("celular", me.getString("aparelho"))
        assertEquals("v1", me.getString("vaga"))
        assertEquals(now, me.getLong("entrouEm"))
        assertEquals(now, me.getLong("vistoEm"))
        assertEquals("u1", room.getJSONObject("vagas").getString("v1"))
        assertEquals(setOf("v1"), keys(room.getJSONObject("vagas")))
        // um PUT só se não existe, com os tempos do servidor (o formato de novaSala do rules.test.mjs)
        val write = fake.writes.single()
        assertEquals("PUT", write.method)
        assertEquals("salas/ABCD", write.path)
        assertEquals("null_etag", write.ifMatch)
        val body = JSONObject(write.body!!)
        assertEquals(setOf("dono", "estado", "criadaEm", "atividadeEm", "jogadores", "vagas"), keys(body))
        assertEquals("timestamp", body.getJSONObject("criadaEm").getString(".sv"))
        assertEquals("timestamp", body.getJSONObject("atividadeEm").getString(".sv"))
        val player = body.getJSONObject("jogadores").getJSONObject("u1")
        assertEquals(setOf("nome", "aparelho", "vaga", "entrouEm", "vistoEm"), keys(player))
        assertEquals("timestamp", player.getJSONObject("entrouEm").getString(".sv"))
        assertEquals("timestamp", player.getJSONObject("vistoEm").getString(".sv"))
    }

    @Test
    fun createOnPcSaysPcAndTrimsTheName() = runTest {
        val repository = fake.repository(device = RoomDevice.PC, random = CodesRandom("ABCD"))

        repository.create("  " + "N".repeat(25) + " ")

        val me = players().getJSONObject("u1")
        assertEquals("pc", me.getString("aparelho"))
        assertEquals("N".repeat(20), me.getString("nome"))
    }

    @Test
    fun createRetriesWhenTheCodeIsTaken() = runTest {
        fake.seedRoom("AAAA", owner = "bia", players = listOf(FakePlayer("bia")))
        val repository = fake.repository(random = CodesRandom("AAAA", "BBBB"))

        assertEquals(CreateResult.Created("BBBB"), repository.create("Ana"))

        assertEquals("bia", room("AAAA")!!.getString("dono"))
        assertEquals("u1", room("BBBB")!!.getString("dono"))
        assertEquals(listOf("PUT", "PUT"), fake.writes.map { it.method }) // a sala viva não é apagada
    }

    @Test
    fun createTakesOverAStaleCode() = runTest {
        fake.seedRoom("AAAA", owner = "bia", players = listOf(FakePlayer("bia")), activityAt = now - 3 * HOUR)
        val repository = fake.repository(random = CodesRandom("AAAA"))

        assertEquals(CreateResult.Created("AAAA"), repository.create("Ana"))

        assertEquals("u1", room("AAAA")!!.getString("dono"))
        assertEquals(setOf("u1"), keys(players("AAAA")))
        assertEquals(now, room("AAAA")!!.getLong("criadaEm"))
        assertEquals(
            listOf("PUT" to "salas/AAAA", "DELETE" to "salas/AAAA", "PUT" to "salas/AAAA"),
            fake.writes.map { it.method to it.path },
        )
    }

    @Test
    fun createKeepsARoomActiveLessThanTwoHoursAgo() = runTest {
        fake.seedRoom("AAAA", owner = "bia", players = listOf(FakePlayer("bia")), activityAt = now - HOUR)
        val repository = fake.repository(random = CodesRandom("AAAA", "BBBB"))

        assertEquals(CreateResult.Created("BBBB"), repository.create("Ana"))

        assertEquals("bia", room("AAAA")!!.getString("dono"))
    }

    @Test
    fun createGoesOnWhenTheStaleRoomCannotBeDeleted() = runTest {
        fake.seedRoom("AAAA", owner = "bia", players = listOf(FakePlayer("bia")), activityAt = now - 3 * HOUR)
        fake.refuse = { it.method == "DELETE" } // o relógio do aparelho adiantado: o servidor acha a sala viva
        val repository = fake.repository(random = CodesRandom("AAAA", "BBBB"))

        assertEquals(CreateResult.Created("BBBB"), repository.create("Ana"))
    }

    /** O servidor que recusa (401) em vez do 412 no código ocupado: relê e, com a sala viva, sorteia outro. */
    @Test
    fun createTreatsARefusedTakenCodeAsTaken() = runTest {
        fake.rulesBeforeEtag = true
        fake.seedRoom("AAAA", owner = "bia", players = listOf(FakePlayer("bia")))
        val repository = fake.repository(random = CodesRandom("AAAA", "BBBB"))

        assertEquals(CreateResult.Created("BBBB"), repository.create("Ana"))

        assertEquals("bia", room("AAAA")!!.getString("dono"))
        assertEquals(listOf("PUT", "PUT"), fake.writes.map { it.method })
    }

    @Test
    fun createTakesOverAStaleCodeRefusedInsteadOf412() = runTest {
        fake.rulesBeforeEtag = true
        fake.seedRoom("AAAA", owner = "bia", players = listOf(FakePlayer("bia")), activityAt = now - 3 * HOUR)
        val repository = fake.repository(random = CodesRandom("AAAA"))

        assertEquals(CreateResult.Created("AAAA"), repository.create("Ana"))

        assertEquals("u1", room("AAAA")!!.getString("dono"))
        assertEquals(listOf("PUT", "DELETE", "PUT"), fake.writes.map { it.method })
    }

    @Test
    fun noInternetOnCreateSaysNoConnection() = runTest {
        fake.online = false

        assertEquals(CreateResult.NoConnection, fake.repository().create("Ana"))
    }

    @Test
    fun refusedCreateSaysNoConnection() = runTest {
        // as regras não publicadas, por exemplo: a tela pede para tentar de novo em vez de quebrar
        fake.refuse = { true }

        assertEquals(CreateResult.NoConnection, fake.repository(random = CodesRandom("ABCD")).create("Ana"))
    }

    // --- entrar ---

    @Test
    fun joinAddsMe() = runTest {
        fake.seedRoom("ABCD", owner = "ana", players = listOf(FakePlayer("ana", joinedAt = now - 1_000)))

        val result = fake.repository(device = RoomDevice.PC).join("ABCD", "Bia")

        assertEquals(JoinResult.Joined("ABCD"), result)
        val me = players().getJSONObject("u1")
        assertEquals("Bia", me.getString("nome"))
        assertEquals("pc", me.getString("aparelho"))
        assertEquals("v2", me.getString("vaga"))
        assertEquals(now, me.getLong("entrouEm"))
        assertEquals(now, me.getLong("vistoEm"))
        assertEquals("u1", room()!!.getJSONObject("vagas").getString("v2"))
        assertEquals(now, room()!!.getLong("atividadeEm"))
        // um PATCH só com o jogador, a vaga e a atividade (o formato de entrar do rules.test.mjs)
        val write = fake.writes.single()
        assertEquals("PATCH" to "salas/ABCD", write.method to write.path)
        assertEquals(setOf("jogadores/u1", "vagas/v2", "atividadeEm"), patchKeys(write))
        val body = JSONObject(write.body!!)
        assertEquals(
            setOf("nome", "aparelho", "vaga", "entrouEm", "vistoEm"),
            keys(body.getJSONObject("jogadores/u1")),
        )
        assertEquals("timestamp", body.getJSONObject("jogadores/u1").getJSONObject("entrouEm").getString(".sv"))
        assertEquals("timestamp", body.getJSONObject("atividadeEm").getString(".sv"))
    }

    @Test
    fun joinUnknownCodeSaysNotFound() = runTest {
        assertEquals(JoinResult.NotFound, fake.repository().join("ZZZZ", "Bia"))

        assertTrue(fake.writes.isEmpty())
    }

    @Test
    fun joinFullRoomSaysFull() = runTest {
        fake.seedRoom("ABCD", owner = "ana", players = listOf("ana", "bia", "caio", "davi").map { FakePlayer(it) })

        assertEquals(JoinResult.Full, fake.repository().join("ABCD", "Eva"))

        assertTrue(fake.writes.isEmpty())
        assertEquals(4, players().length())
    }

    @Test
    fun joinTakesAnAbandonedSlot() = runTest {
        fake.seedRoom("ABCD", owner = "ana", players = listOf("ana", "bia", "caio", "davi").map { FakePlayer(it) })
        fake.set("salas/ABCD/jogadores/caio", "null") // o caio saiu sem liberar a v3

        assertEquals(JoinResult.Joined("ABCD"), fake.repository().join("ABCD", "Eva"))

        assertEquals("v3", players().getJSONObject("u1").getString("vaga"))
        assertEquals("u1", room()!!.getJSONObject("vagas").getString("v3"))
    }

    @Test
    fun joinTriesAnotherSlotWhenRefused() = runTest {
        fake.seedRoom("ABCD", owner = "ana", players = listOf(FakePlayer("ana")))
        // a bia ocupa a v2 entre a leitura e a escrita: as regras recusam, e o app relê e tenta a v3
        var first = true
        fake.refuse = {
            if (first) fake.seedRoom("ABCD", owner = "ana", players = listOf(FakePlayer("ana"), FakePlayer("bia")))
            first.also { first = false }
        }

        assertEquals(JoinResult.Joined("ABCD"), fake.repository().join("ABCD", "Eva"))

        assertEquals("v3", players().getJSONObject("u1").getString("vaga"))
        assertEquals(listOf(setOf("jogadores/u1", "vagas/v2", "atividadeEm")), fake.writes.take(1).map(::patchKeys))
    }

    @Test
    fun joinRefusedAndThenFullSaysFull() = runTest {
        fake.seedRoom("ABCD", owner = "ana", players = listOf("ana", "bia", "caio").map { FakePlayer(it) })
        // dois entrando juntos com 3 na sala: o outro ficou com a última vaga
        fake.refuse = {
            fake.seedRoom("ABCD", owner = "ana", players = listOf("ana", "bia", "caio", "davi").map { FakePlayer(it) })
            true
        }

        assertEquals(JoinResult.Full, fake.repository().join("ABCD", "Eva"))
    }

    @Test
    fun joinAlwaysRefusedSaysNoConnection() = runTest {
        fake.seedRoom("ABCD", owner = "ana", players = listOf(FakePlayer("ana")))
        fake.refuse = { true }

        assertEquals(JoinResult.NoConnection, fake.repository().join("ABCD", "Eva"))
    }

    @Test
    fun joinTwiceKeepsOneEntry() = runTest {
        fake.seedRoom("ABCD", owner = "ana", players = listOf(FakePlayer("ana")))
        val repository = fake.repository()
        repository.join("ABCD", "Bia")
        val joinedAt = now
        now += 30_000

        assertEquals(JoinResult.Joined("ABCD"), repository.join("ABCD", "Bia"))

        assertEquals(setOf("ana", "u1"), keys(players()))
        val me = players().getJSONObject("u1")
        assertEquals(joinedAt, me.getLong("entrouEm")) // continua na ordem de quem entrou
        assertEquals(now, me.getLong("vistoEm"))
        assertEquals("v2", me.getString("vaga"))
        assertEquals(setOf("v1", "v2"), keys(room()!!.getJSONObject("vagas")))
        // o entrouEm regravado com o mesmo valor (o que as regras aceitam em joinTwiceKeepsOneEntry)
        val body = JSONObject(fake.writes.last().body!!)
        assertEquals(joinedAt, body.getJSONObject("jogadores/u1").getLong("entrouEm"))
    }

    @Test
    fun joinWithoutInternetSaysNoConnection() = runTest {
        fake.seedRoom("ABCD", owner = "ana", players = listOf(FakePlayer("ana")))
        fake.online = false

        assertEquals(JoinResult.NoConnection, fake.repository().join("ABCD", "Bia"))
    }

    // --- sair ---

    @Test
    fun leaveAsOwnerPassesToOldestPlayer() = runTest {
        fake.seedRoom(
            "ABCD",
            owner = "u1",
            players = listOf(
                FakePlayer("u1", joinedAt = now - 3_000),
                FakePlayer("caio", joinedAt = now - 1_000),
                FakePlayer("bia", joinedAt = now - 2_000),
            ),
        )

        fake.repository().leave("ABCD")

        assertEquals("bia", room()!!.getString("dono"))
        assertEquals(setOf("bia", "caio"), keys(players()))
        assertEquals(setOf("v2", "v3"), keys(room()!!.getJSONObject("vagas")))
        // numa escrita só: o novo dono, o jogador e a vaga (ownerRemovesPlayersAndLeavesPassingTheOwner)
        val write = fake.writes.single()
        assertEquals("PATCH" to "salas/ABCD", write.method to write.path)
        assertEquals(setOf("dono", "jogadores/u1", "vagas/v1"), patchKeys(write))
    }

    @Test
    fun leaveAsOwnerPrefersALivePlayer() = runTest {
        fake.seedRoom(
            "ABCD",
            owner = "u1",
            players = listOf(
                FakePlayer("u1", joinedAt = now - 3_000),
                FakePlayer("bia", joinedAt = now - 2_000, seenAt = now - 2 * MINUTE),
                FakePlayer("caio", joinedAt = now - 1_000),
            ),
        )

        fake.repository().leave("ABCD")

        assertEquals("caio", room()!!.getString("dono"))
    }

    @Test
    fun leaveAsGuestRemovesOnlyMe() = runTest {
        fake.seedRoom("ABCD", owner = "ana", players = listOf(FakePlayer("ana"), FakePlayer("u1")))

        fake.repository().leave("ABCD")

        assertEquals("ana", room()!!.getString("dono"))
        assertEquals(setOf("ana"), keys(players()))
        assertEquals(setOf("v1"), keys(room()!!.getJSONObject("vagas")))
        assertEquals(setOf("jogadores/u1", "vagas/v2"), patchKeys(fake.writes.single()))
    }

    @Test
    fun leaveAsLastPlayerDeletesTheRoom() = runTest {
        val repository = fake.repository(random = CodesRandom("ABCD"))
        repository.create("Ana")

        repository.leave("ABCD")

        assertNull(room())
        assertEquals("DELETE" to "salas/ABCD", fake.writes.last().let { it.method to it.path })
    }

    @Test
    fun leaveAsLastPlayerAfterTheOwnerVanishedTakesTheRoomAndDeletesIt() = runTest {
        fake.seedRoom("ABCD", owner = "ana", players = listOf(FakePlayer("ana"), FakePlayer("u1")))
        fake.set("salas/ABCD/jogadores/ana", "null")

        fake.repository().leave("ABCD")

        assertNull(room())
        assertEquals(listOf("PATCH", "DELETE"), fake.writes.map { it.method })
        assertEquals(setOf("dono"), patchKeys(fake.writes.first()))
    }

    @Test
    fun leaveWithoutInternetDoesNotThrow() = runTest {
        fake.seedRoom("ABCD", owner = "ana", players = listOf(FakePlayer("ana"), FakePlayer("u1")))
        val repository = fake.repository()
        repository.join("ABCD", "Bia")
        fake.online = false

        repository.leave("ABCD") // quem saiu some em 60 s e as regras deixam o dono passar adiante
    }

    /** A sala em que o app está (o PC sai dela ao fechar a janela): criar e entrar marcam, sair desmarca. */
    @Test
    fun joinedFollowsCreateJoinAndLeave() = runTest {
        val repository = fake.repository(random = CodesRandom("ABCD"))
        assertNull(repository.joined)

        repository.create("Ana")
        assertEquals("ABCD", repository.joined)
        repository.leave("ABCD")
        assertNull(repository.joined)

        fake.seedRoom("EFGH", owner = "bia", players = listOf(FakePlayer("bia")))
        assertEquals(JoinResult.NotFound, repository.join("ZZZZ", "Ana"))
        assertNull(repository.joined)
        repository.join("EFGH", "Ana")
        assertEquals("EFGH", repository.joined)
    }

    @Test
    fun leaveARoomThatIsGoneDoesNothing() = runTest {
        fake.repository().leave("ZZZZ")

        assertTrue(fake.writes.isEmpty())
    }

    // --- batimento e o dono que some ---

    @Test
    fun heartbeatTouchesSeenAndActivity() = runTest {
        fake.seedRoom("ABCD", owner = "ana", players = listOf(FakePlayer("ana"), FakePlayer("u1")))
        now += 20_000

        fake.repository().heartbeat("ABCD")

        assertEquals(now, players().getJSONObject("u1").getLong("vistoEm"))
        assertEquals(now, room()!!.getLong("atividadeEm"))
        val write = fake.writes.single()
        assertEquals("PATCH" to "salas/ABCD", write.method to write.path)
        assertEquals(setOf("jogadores/u1/vistoEm", "atividadeEm"), patchKeys(write))
    }

    @Test
    fun heartbeatOutsideTheRoomWritesNothing() = runTest {
        fake.seedRoom("ABCD", owner = "ana", players = listOf(FakePlayer("ana")))

        fake.repository().heartbeat("ABCD")
        fake.repository().heartbeat("ZZZZ")
        fake.online = false
        fake.repository().heartbeat("ABCD")

        assertTrue(fake.writes.isEmpty())
    }

    @Test
    fun goneOwnerIsReplacedByOldestLivePlayer() = runTest {
        fake.seedRoom(
            "ABCD",
            owner = "ana",
            players = listOf(
                FakePlayer("ana", joinedAt = now - 4_000, seenAt = now - 90_000),
                FakePlayer("bia", joinedAt = now - 3_000, seenAt = now - 90_000), // também sumiu
                FakePlayer("caio", joinedAt = now - 2_000),
                FakePlayer("u1", joinedAt = now - 1_000),
            ),
        )

        fake.repository("u1").heartbeat("ABCD")
        assertEquals("ana", room()!!.getString("dono")) // o u1 não é o mais antigo vivo

        fake.repository("caio").heartbeat("ABCD")
        assertEquals("caio", room()!!.getString("dono"))
        val write = fake.writes.last()
        assertEquals("caio" to setOf("dono"), write.uid to patchKeys(write))
    }

    @Test
    fun ownerSilentLessThan60sStaysOwner() = runTest {
        fake.seedRoom(
            "ABCD",
            owner = "ana",
            players = listOf(
                FakePlayer("ana", joinedAt = now - 4_000, seenAt = now - 30_000),
                FakePlayer("caio", joinedAt = now - 2_000),
            ),
        )

        fake.repository("caio").heartbeat("ABCD")

        assertEquals("ana", room()!!.getString("dono"))
        assertEquals(1, fake.writes.size)
    }

    @Test
    fun ownerMissingFromPlayersIsReplaced() = runTest {
        fake.seedRoom("ABCD", owner = "ana", players = listOf(FakePlayer("ana"), FakePlayer("caio")))
        fake.set("salas/ABCD/jogadores/ana", "null")

        fake.repository("caio").heartbeat("ABCD")

        assertEquals("caio", room()!!.getString("dono"))
    }

    // --- o dono limpa quem sumiu ---

    /** O dono, no batimento, tira quem sumiu há mais de 5 min (jogador e vaga, numa escrita só). */
    @Test
    fun ownerRemovesPlayersGoneForFiveMinutes() = runTest {
        fake.seedRoom(
            "ABCD",
            owner = "u1",
            players = listOf(
                FakePlayer("u1", joinedAt = now - 4_000),
                FakePlayer("bia", joinedAt = now - 3_000, seenAt = now - 5 * MINUTE - 1),
                FakePlayer("caio", joinedAt = now - 2_000, seenAt = now - 5 * MINUTE),
            ),
        )

        fake.repository().heartbeat("ABCD")

        assertEquals(setOf("u1", "caio"), keys(players()))
        assertEquals(setOf("v1", "v3"), keys(room()!!.getJSONObject("vagas")))
        val write = fake.writes.last()
        assertEquals("PATCH" to "salas/ABCD", write.method to write.path)
        assertEquals(setOf("jogadores/bia", "vagas/v2"), patchKeys(write))
    }

    /** A vaga que já é de outro (o sumido saiu sem liberar e alguém entrou nela) não é mexida. */
    @Test
    fun ownerRemovesTheVanishedPlayerButNotASlotTakenByAnother() = runTest {
        fake.seedRoom(
            "ABCD",
            owner = "u1",
            players = listOf(FakePlayer("u1"), FakePlayer("bia", seenAt = now - 10 * MINUTE), FakePlayer("eva")),
        )
        fake.set("salas/ABCD/vagas/v2", "\"eva\"")
        fake.set("salas/ABCD/jogadores/eva/vaga", "\"v2\"")

        fake.repository().heartbeat("ABCD")

        assertEquals(setOf("u1", "eva"), keys(players()))
        assertEquals(setOf("jogadores/bia"), patchKeys(fake.writes.last()))
    }

    /** O tempo é o do servidor (o mais novo gravado na sala), não o relógio do aparelho. */
    @Test
    fun vanishedIsMeasuredWithServerTimeNotTheDeviceClock() = runTest {
        fake.seedRoom(
            "ABCD",
            owner = "u1",
            players = listOf(
                FakePlayer("u1"),
                FakePlayer("bia", seenAt = now - MINUTE),
                FakePlayer("caio", seenAt = now - 6 * MINUTE),
            ),
        )
        val deviceAhead = fake.repository(deviceClock = { now + HOUR })

        deviceAhead.heartbeat("ABCD")

        assertEquals(setOf("u1", "bia"), keys(players()))
    }

    @Test
    fun guestDoesNotRemoveVanishedPlayers() = runTest {
        fake.seedRoom(
            "ABCD",
            owner = "ana",
            players = listOf(FakePlayer("ana"), FakePlayer("u1"), FakePlayer("bia", seenAt = now - 10 * MINUTE)),
        )

        fake.repository().heartbeat("ABCD")

        assertEquals(setOf("ana", "u1", "bia"), keys(players()))
        assertEquals(1, fake.writes.size) // só o batimento
    }

    // --- a sala ao vivo ---

    @Test
    fun observeEmitsPlayersInJoinOrder() = runTest {
        fake.seedRoom(
            "ABCD",
            owner = "ana",
            players = listOf(
                FakePlayer("ana", name = "Ana", joinedAt = now - 3_000),
                FakePlayer("caio", name = "Caio", device = RoomDevice.PC, joinedAt = now - 1_000),
                FakePlayer("bia", name = "Bia", joinedAt = now - 2_000),
            ),
        )

        val room = fake.repository("bia").observe("ABCD").first()!!

        assertEquals("ABCD", room.code)
        assertEquals("ana", room.owner)
        assertEquals("bia", room.me)
        assertFalse(room.iAmOwner)
        assertEquals(listOf("ana", "bia", "caio"), room.players.map { it.uid })
        assertEquals(
            RoomPlayer("caio", "Caio", RoomDevice.PC, joinedAt = now - 1_000, seenAt = now, gone = false),
            room.players.last(),
        )
        assertTrue(fake.repository("ana").observe("ABCD").first()!!.iAmOwner)
    }

    @Test
    fun observeShowsAnotherPlayerJoiningLive() = runTest {
        fake.repository(random = CodesRandom("ABCD")).create("Ana")
        val rooms = mutableListOf<Room?>()
        backgroundScope.launch { fake.repository().observe("ABCD").toList(rooms) }
        runCurrent()

        now += 1_000
        fake.repository("bia", RoomDevice.PC).join("ABCD", "Bia")
        runCurrent()

        assertEquals(listOf(listOf("Ana"), listOf("Ana", "Bia")), rooms.map { room -> room!!.players.map { it.name } })
    }

    @Test
    fun observeEmitsNullWhenRoomIsDeleted() = runTest {
        fake.seedRoom("ABCD", owner = "ana", players = listOf(FakePlayer("ana"), FakePlayer("u1")))
        val rooms = mutableListOf<Room?>()
        backgroundScope.launch { fake.repository().observe("ABCD").toList(rooms) }
        runCurrent()

        fake.set("salas/ABCD", "null")
        runCurrent()

        assertEquals(2, rooms.size)
        assertEquals("ana", rooms.first()!!.owner)
        assertNull(rooms.last())
    }

    /** Tirado da sala (o dono me limpou depois de 5 min sumido): para mim a sala acabou. */
    @Test
    fun observeEmitsNullWhenIAmNoLongerInTheRoom() = runTest {
        fake.seedRoom("ABCD", owner = "ana", players = listOf(FakePlayer("ana"), FakePlayer("u1")))
        val rooms = mutableListOf<Room?>()
        backgroundScope.launch { fake.repository().observe("ABCD").toList(rooms) }
        runCurrent()

        fake.set("salas/ABCD/jogadores/u1", "null")
        runCurrent()

        assertEquals(2, rooms.size)
        assertNull(rooms.last())
    }

    /** A sala acabou e outra foi criada com o mesmo código: não é a minha sala. */
    @Test
    fun observeEmitsNullForARoomRecreatedWithTheSameCode() = runTest {
        fake.seedRoom("ABCD", owner = "u1", players = listOf(FakePlayer("u1")))
        val rooms = mutableListOf<Room?>()
        backgroundScope.launch { fake.repository().observe("ABCD").toList(rooms) }
        runCurrent()

        fake.seedRoom("ABCD", owner = "zeca", players = listOf(FakePlayer("zeca")))
        runCurrent()

        assertEquals("u1", rooms.first()!!.owner)
        assertNull(rooms.last())
    }

    @Test
    fun playerSilentFor60sIsGone() = runTest {
        fake.seedRoom("ABCD", owner = "ana", players = listOf(FakePlayer("ana"), FakePlayer("bia"), FakePlayer("u1")))
        val rooms = mutableListOf<Room?>()
        backgroundScope.launch { fake.repository().observe("ABCD").toList(rooms) }
        runCurrent()

        now += 60_000 // 60 s ainda não é sumir
        advanceTimeBy(5_001)
        assertEquals(1, rooms.size)

        now += 1
        advanceTimeBy(5_000) // sem nenhum evento do banco: o recálculo a cada 5 s
        val bia = rooms.last()!!.players.single { it.uid == "bia" }
        assertTrue(bia.gone)
        assertEquals(2, rooms.size)
    }

    private companion object {
        const val T0 = 1_800_000_000_000L
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
