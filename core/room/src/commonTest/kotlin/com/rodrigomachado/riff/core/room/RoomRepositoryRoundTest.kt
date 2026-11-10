package com.rodrigomachado.riff.core.room

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.room.testing.FakeFirebase
import com.rodrigomachado.riff.core.room.testing.FakePlayer
import com.rodrigomachado.riff.core.room.testing.FirebaseWrite
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** O [RoomRepository] na M2: a música, o Pronto, a rodada, o relógio e o começo junto. */
@OptIn(ExperimentalCoroutinesApi::class) // o relógio virtual do runTest
class RoomRepositoryRoundTest : JsonTest() {
    private var now = T0
    private val fake = FakeFirebase(clock = { now }, uid = "u1")

    private fun room(code: String = "ABCD") = fake.get("salas/$code") as JSONObject?

    private fun players(code: String = "ABCD") = room(code)!!.getJSONObject("jogadores")

    private fun keys(json: JSONObject) = json.keys().asSequence().toSet()

    /** As chaves do corpo de um PATCH (os caminhos que ele grava). */
    private fun patchKeys(write: FirebaseWrite) = keys(JSONObject(write.body!!))

    /** A sala com a música [SONG_JSON] e os [players], u1 (eu) como dono por padrão. */
    private fun seedSongRoom(
        vararg players: FakePlayer,
        owner: String = "u1",
        state: String = "sala",
        round: Int? = null,
        startAt: Long? = null,
    ) = fake.seedRoom(
        "ABCD",
        owner = owner,
        players = players.toList(),
        state = state,
        round = round,
        songJson = SONG_JSON,
        startAt = startAt,
    )

    private fun readyPlayer(uid: String, loadedRound: Int? = null, seenAt: Long? = null) = FakePlayer(
        uid,
        seenAt = seenAt,
        difficulty = Difficulty.HARD,
        ready = true,
        song = SongStatus.HAS,
        loadedRound = loadedRound,
    )

    /** A sala vista por [uid] agora (uma leitura do streaming). */
    private suspend fun observed(uid: String = "u1") = fake.repository(uid).observe("ABCD").first()!!

    @Test
    fun chooseSongWritesTheSongAndMyDifficulty() = runTest {
        fake.seedRoom("ABCD", owner = "u1", players = listOf(FakePlayer("u1"), FakePlayer("bia")))

        fake.repository().chooseSong("ABCD", SONG, Difficulty.EXPERT)

        val write = fake.writes.single()
        assertEquals("PATCH" to "salas/ABCD", write.method to write.path)
        assertEquals(
            setOf("musica", "jogadores/u1/dificuldade", "jogadores/u1/musica", "jogadores/u1/pronto", "atividadeEm"),
            patchKeys(write),
        )
        val body = JSONObject(write.body!!)
        val song = body.getJSONObject("musica")
        assertEquals(setOf("chartId", "titulo", "artista", "dificuldades", "duracaoMs", "encore"), keys(song))
        assertEquals("EASY,HARD,EXPERT", song.getString("dificuldades")) // na ordem das dificuldades
        assertEquals(setOf("md5", "video", "charter"), keys(song.getJSONObject("encore")))
        assertEquals("EXPERT", body.getString("jogadores/u1/dificuldade"))
        assertEquals("tem", body.getString("jogadores/u1/musica"))
        assertFalse(body.getBoolean("jogadores/u1/pronto"))
        assertEquals("timestamp", body.getJSONObject("atividadeEm").getString(".sv"))
        val room = observed()
        assertEquals(SONG, room.song)
        val me = room.players.single { it.uid == "u1" }
        assertEquals(Difficulty.EXPERT, me.difficulty)
        assertEquals(SongStatus.HAS, me.song)
        assertFalse(me.ready)
    }

    @Test
    fun chooseSongWithoutEncoreOrDurationLeavesThemOut() = runTest {
        fake.seedRoom("ABCD", owner = "u1", players = listOf(FakePlayer("u1")))
        val song = SONG.copy(durationMs = null, encore = null)

        fake.repository().chooseSong("ABCD", song, Difficulty.EASY)

        assertEquals(
            setOf("chartId", "titulo", "artista", "dificuldades"),
            keys(JSONObject(fake.writes.single().body!!).getJSONObject("musica")),
        )
        assertEquals(song, observed().song)
        val withoutCharter = SONG.copy(encore = EncoreRef(md5 = "0".repeat(32), video = false, charter = null))
        fake.repository().chooseSong("ABCD", withoutCharter, Difficulty.EASY)
        assertEquals(withoutCharter, observed().song)
    }

    /** O limite das regras (200) para o título, o artista e quem fez o mapa: o que passa é cortado. */
    @Test
    fun chooseSongCutsLongTextsTo200() = runTest {
        fake.seedRoom("ABCD", owner = "u1", players = listOf(FakePlayer("u1")))
        val long = SONG.copy(
            title = "t".repeat(250),
            artist = "a".repeat(201),
            encore = SONG.encore!!.copy(charter = "c".repeat(300)),
        )

        assertTrue(fake.repository().chooseSong("ABCD", long, Difficulty.EASY))

        val song = JSONObject(fake.writes.single().body!!).getJSONObject("musica")
        assertEquals(200, song.getString("titulo").length)
        assertEquals(200, song.getString("artista").length)
        assertEquals(200, song.getJSONObject("encore").getString("charter").length)
    }

    /** Um mapa sem dificuldades as regras recusam: nem tenta. Recusado ou sem internet: false. */
    @Test
    fun chooseSongWithoutDifficultiesOrRefusedSaysFalse() = runTest {
        fake.seedRoom("ABCD", owner = "u1", players = listOf(FakePlayer("u1")))
        val repository = fake.repository()

        assertFalse(repository.chooseSong("ABCD", SONG.copy(difficulties = emptySet()), Difficulty.EASY))
        assertTrue(fake.writes.isEmpty())

        fake.refuse = { true }
        assertFalse(repository.chooseSong("ABCD", SONG, Difficulty.EASY))
        fake.refuse = { false }
        fake.online = false
        assertFalse(repository.chooseSong("ABCD", SONG, Difficulty.EASY))
    }

    /** Sair esquece a contagem vista: na sala nova com o mesmo código, os 15 s contam de novo. */
    @Test
    fun leavingForgetsTheCountdownSeen() = runTest {
        seedSongRoom(readyPlayer("u1"), readyPlayer("bia"))
        val repository = fake.repository()
        assertTrue(repository.startRound("ABCD"))
        repository.leave("ABCD")

        now += 60_000
        seedSongRoom(readyPlayer("u1"), readyPlayer("bia"), state = "contagem", round = 1)
        repository.scheduleStartIfDue("ABCD", 1)

        assertEquals("contagem", room()!!.getString("estado")) // conta de agora, não de 60 s atrás
    }

    /** Sair esquece o primeiro Pronto visto: na sala nova com o mesmo código, os 30 s contam de novo. */
    @Test
    fun leavingForgetsTheFirstReadySeen() = runTest {
        seedSongRoom(readyPlayer("u1"), FakePlayer("bia"))
        val repository = fake.repository()
        assertEquals(now, repository.observe("ABCD").first()!!.firstReadyAt)
        repository.leave("ABCD")

        now += 60_000
        seedSongRoom(readyPlayer("u1"), FakePlayer("bia"))

        assertEquals(now, repository.observe("ABCD").first()!!.firstReadyAt)
    }

    @Test
    fun setMeWritesMyState() = runTest {
        seedSongRoom(FakePlayer("u1"), FakePlayer("bia"))
        val bia = fake.repository("bia")

        bia.setMe("ABCD", Difficulty.HARD, ready = true, song = SongStatus.HAS)

        val write = fake.writes.single()
        assertEquals(
            setOf("jogadores/bia/dificuldade", "jogadores/bia/pronto", "jogadores/bia/musica", "atividadeEm"),
            patchKeys(write),
        )
        val me = observed().players.single { it.uid == "bia" }
        assertEquals(Triple(Difficulty.HARD, true, SongStatus.HAS), Triple(me.difficulty, me.ready, me.song))

        bia.setMe("ABCD", difficulty = null, ready = false, song = SongStatus.DOWNLOADING)

        assertTrue(JSONObject(fake.writes.last().body!!).isNull("jogadores/bia/dificuldade"))
        val again = observed().players.single { it.uid == "bia" }
        assertEquals(Triple(null, false, SongStatus.DOWNLOADING), Triple(again.difficulty, again.ready, again.song))
    }

    @Test
    fun meWithoutTheM2FieldsReadsAsNotReady() = runTest {
        fake.seedRoom("ABCD", owner = "u1", players = listOf(FakePlayer("u1")))

        val room = observed()

        assertEquals(null, room.song)
        assertEquals(RoomState.LOBBY to 0, room.state to room.round)
        assertNull(room.startAt)
        val me = room.players.single()
        assertEquals(
            listOf(null, false, null, null, null),
            listOf(me.difficulty, me.ready, me.song, me.loadedRound, me.leftRound),
        )
    }

    @Test
    fun canStartNeedsTwoReadyWithTheSong() = runTest {
        val notReady = { uid: String -> FakePlayer(uid, difficulty = Difficulty.HARD, song = SongStatus.HAS) }
        seedSongRoom(notReady("u1"), notReady("bia"), notReady("caio"))
        val rooms = mutableListOf<Room?>()
        backgroundScope.launch { fake.repository().observe("ABCD").toList(rooms) }
        runCurrent()
        assertNull(rooms.last()!!.firstReadyAt)

        now += 1_000
        val firstReady = now
        fake.repository("bia").setMe("ABCD", Difficulty.HARD, ready = true, song = SongStatus.HAS)
        runCurrent()
        assertFalse(rooms.last()!!.canStart(now)) // 1 pronto

        now += 4_000
        fake.repository().setMe("ABCD", Difficulty.EXPERT, ready = true, song = SongStatus.HAS)
        runCurrent()
        val room = rooms.last()!!
        assertEquals(firstReady, room.firstReadyAt) // o primeiro Pronto, não o último
        assertFalse(room.canStart(now)) // o caio tem a música e não está pronto
        assertFalse(room.canStart(firstReady + 29_999))
        assertTrue(room.canStart(firstReady + 30_000)) // "Começar com quem está pronto"

        fake.repository("caio").setMe("ABCD", Difficulty.EASY, ready = true, song = SongStatus.HAS)
        runCurrent()
        assertTrue(rooms.last()!!.canStart(now))
    }

    @Test
    fun firstReadyStartsOverWithANewSongOrNobodyReady() = runTest {
        seedSongRoom(readyPlayer("u1"), FakePlayer("bia"))
        val rooms = mutableListOf<Room?>()
        backgroundScope.launch { fake.repository().observe("ABCD").toList(rooms) }
        runCurrent()
        assertEquals(now, rooms.last()!!.firstReadyAt) // já pronto ao abrir: conta a partir do que se viu

        now += 10_000
        fake.repository().setMe("ABCD", Difficulty.HARD, ready = false, song = SongStatus.HAS)
        runCurrent()
        assertNull(rooms.last()!!.firstReadyAt)

        now += 10_000
        fake.repository().setMe("ABCD", Difficulty.HARD, ready = true, song = SongStatus.HAS)
        runCurrent()
        assertEquals(now, rooms.last()!!.firstReadyAt)

        now += 10_000
        fake.repository("bia").setMe("ABCD", Difficulty.HARD, ready = true, song = SongStatus.HAS)
        fake.repository().chooseSong("ABCD", SONG.copy(chartId = "b".repeat(40)), Difficulty.HARD)
        runCurrent()
        assertEquals(now, rooms.last()!!.firstReadyAt) // outra música: a bia (ainda pronta) conta de agora
    }

    @Test
    fun missingSongPlayerDoesNotBlock() = runTest {
        seedSongRoom(readyPlayer("u1"), readyPlayer("bia"), FakePlayer("caio", song = SongStatus.MISSING))

        assertTrue(observed().canStart(now))
        assertTrue(fake.repository().startRound("ABCD"))
    }

    @Test
    fun goneDoesNotBlock() = runTest {
        seedSongRoom(
            readyPlayer("u1"),
            readyPlayer("bia"),
            FakePlayer("caio", seenAt = now - 90_000, ready = false, song = SongStatus.HAS),
        )

        assertTrue(observed().canStart(now))
        assertTrue(fake.repository().startRound("ABCD"))
    }

    @Test
    fun startRoundRaisesTheRoundAndCountsDown() = runTest {
        seedSongRoom(readyPlayer("u1"), readyPlayer("bia"), round = 2)

        assertTrue(fake.repository().startRound("ABCD"))

        val write = fake.writes.last()
        assertEquals("PATCH" to "salas/ABCD", write.method to write.path)
        assertEquals(setOf("estado", "rodada", "inicio", "atividadeEm"), patchKeys(write))
        val body = JSONObject(write.body!!)
        assertEquals("contagem", body.getString("estado"))
        assertEquals(3, body.getInt("rodada"))
        assertTrue(body.isNull("inicio"))
        val room = observed()
        assertEquals(RoomState.COUNTDOWN to 3, room.state to room.round)
        assertNull(room.startAt)
        assertEquals(setOf("u1", "bia"), room.playersInRound().map { it.uid }.toSet())
    }

    @Test
    fun startRoundWithOneReadyDoesNothing() = runTest {
        seedSongRoom(readyPlayer("u1"), FakePlayer("bia", difficulty = Difficulty.HARD, song = SongStatus.HAS))

        assertFalse(fake.repository().startRound("ABCD"))

        assertTrue(fake.writes.none { "estado" in patchKeys(it) })
        assertEquals("sala", room()!!.getString("estado"))
    }

    @Test
    fun startRoundWithTheOwnerNotReadyDoesNothing() = runTest {
        val owner = FakePlayer("u1", difficulty = Difficulty.HARD, song = SongStatus.HAS)
        seedSongRoom(owner, readyPlayer("bia"), readyPlayer("caio"))
        val repository = fake.repository()
        assertFalse(repository.startRound("ABCD"))

        now += 60_000

        assertFalse(repository.startRound("ABCD")) // nem depois dos 30 s
        assertEquals("sala", room()!!.getString("estado"))
    }

    @Test
    fun startRoundAfter30sStartsWithWhoIsReady() = runTest {
        val notReady = FakePlayer("caio", difficulty = Difficulty.HARD, song = SongStatus.HAS)
        seedSongRoom(readyPlayer("u1"), readyPlayer("bia"), notReady)
        val repository = fake.repository()
        assertFalse(repository.startRound("ABCD")) // o primeiro Pronto visto agora

        now += 30_000

        assertTrue(repository.startRound("ABCD"))
        assertEquals("contagem", room()!!.getString("estado"))
    }

    @Test
    fun guestCannotStartRound() = runTest {
        seedSongRoom(readyPlayer("u1"), readyPlayer("bia"))

        assertFalse(fake.repository("bia").startRound("ABCD"))

        assertTrue(fake.writes.isEmpty())
        assertEquals("sala", room()!!.getString("estado"))
    }

    @Test
    fun startRoundWithoutInternetSaysFalse() = runTest {
        seedSongRoom(readyPlayer("u1"), readyPlayer("bia"))
        fake.online = false

        assertFalse(fake.repository().startRound("ABCD"))
    }

    @Test
    fun ownerStartsWhenAllLoaded() = runTest {
        seedSongRoom(readyPlayer("u1"), readyPlayer("bia"), state = "contagem", round = 1)
        val owner = fake.repository()
        fake.repository("bia").markLoaded("ABCD", 1)
        assertEquals(setOf("jogadores/bia/carregou"), patchKeys(fake.writes.last()))

        owner.scheduleStartIfDue("ABCD", 1)
        assertEquals("contagem", room()!!.getString("estado")) // falto eu

        owner.markLoaded("ABCD", 1)
        owner.scheduleStartIfDue("ABCD", 1)

        val write = fake.writes.last()
        assertEquals(setOf("estado", "inicio", "atividadeEm"), patchKeys(write))
        assertEquals("partida", room()!!.getString("estado"))
        assertEquals(now + 3_000, room()!!.getLong("inicio"))
        val room = observed()
        assertEquals(RoomState.PLAYING to now + 3_000, room.state to room.startAt)
        assertEquals(listOf(1, 1), room.players.map { it.loadedRound })
    }

    @Test
    fun ownerStartsWithWhoLoadedAfter15s() = runTest {
        seedSongRoom(readyPlayer("u1"), readyPlayer("bia"))
        val owner = fake.repository()
        assertTrue(owner.startRound("ABCD"))
        owner.markLoaded("ABCD", 1) // a bia nunca carrega

        now += 14_999
        owner.scheduleStartIfDue("ABCD", 1)
        assertEquals("contagem", room()!!.getString("estado"))

        now += 1
        owner.scheduleStartIfDue("ABCD", 1)
        assertEquals("partida", room()!!.getString("estado"))
        assertEquals(now + 3_000, room()!!.getLong("inicio"))
    }

    @Test
    fun scheduleStartForAnotherRoundOrStateDoesNothing() = runTest {
        val loaded = listOf(readyPlayer("u1", loadedRound = 2), readyPlayer("bia", loadedRound = 2))
        seedSongRoom(*loaded.toTypedArray(), state = "contagem", round = 2)
        val owner = fake.repository()

        owner.scheduleStartIfDue("ABCD", 1)
        assertEquals("contagem", room()!!.getString("estado"))

        fake.seedRoom("ABCD", owner = "u1", players = listOf(readyPlayer("u1"), readyPlayer("bia")), round = 2)
        owner.scheduleStartIfDue("ABCD", 2)
        assertEquals("sala", room()!!.getString("estado"))
        fake.repository("bia").scheduleStartIfDue("ABCD", 2) // convidado
        assertEquals("sala", room()!!.getString("estado"))
    }

    @Test
    fun newOwnerFinishesTheCountdown() = runTest {
        seedSongRoom(
            FakePlayer("ana", seenAt = now - 90_000, difficulty = Difficulty.HARD, ready = true, song = SongStatus.HAS),
            readyPlayer("u1", loadedRound = 1),
            owner = "ana",
            state = "contagem",
            round = 1,
        )
        val me = fake.repository()
        me.scheduleStartIfDue("ABCD", 1)
        assertEquals("contagem", room()!!.getString("estado")) // ainda não sou o dono

        me.heartbeat("ABCD") // o dono sumiu: assumo (escrita só do dono)
        me.scheduleStartIfDue("ABCD", 1) // e depois o começo, noutra escrita

        assertEquals("u1", room()!!.getString("dono"))
        assertEquals("partida", room()!!.getString("estado"))
        assertEquals(now + 3_000, room()!!.getLong("inicio"))
        val ownerWrite = fake.writes.indexOfFirst { "dono" in patchKeys(it) }
        val startWrite = fake.writes.indexOfFirst { "inicio" in patchKeys(it) }
        assertTrue(ownerWrite in 0 until startWrite)
        assertEquals(setOf("dono"), patchKeys(fake.writes[ownerWrite]))
    }

    @Test
    fun measureClockUsesTheFastestRoundTrip() = runTest {
        fake.seedRoom("ABCD", owner = "u1", players = listOf(FakePlayer("u1")))
        val device = fake.repository(deviceClock = { now - 5 * MINUTE }) // o servidor 5 min adiantado
        // 3 idas e voltas (PATCH + GET): 300 ms com a demora toda antes do carimbo, 80 ms igual nas 4 metades,
        // 200 ms com a demora toda depois do carimbo
        val trips = ArrayDeque(
            listOf(listOf(300L, 0L, 0L, 0L), listOf(20L, 20L, 20L, 20L), listOf(0L, 100L, 100L, 0L)),
        )
        var leg = 0
        fake.onTrip = { _, path, _ ->
            if (path.endsWith("/vistoEm") || path == "salas/ABCD") {
                now += trips.first()[leg]
                leg++
                if (leg == 4) {
                    trips.removeFirst()
                    leg = 0
                }
            }
        }

        val clock = device.measureClock("ABCD")

        assertTrue(trips.isEmpty())
        assertTrue("offset ${clock.offsetMs}", abs(clock.offsetMs - 5 * MINUTE) <= 40)
        val clockWrites = fake.writes.filter { it.method == "PATCH" }
        assertEquals(3, clockWrites.size)
        assertEquals(setOf("jogadores/u1/vistoEm", "atividadeEm"), patchKeys(clockWrites.first()))
    }

    @Test
    fun measureClockWithoutInternetThrows() = runTest {
        fake.seedRoom("ABCD", owner = "u1", players = listOf(FakePlayer("u1")))
        val repository = fake.repository()
        repository.observe("ABCD").first() // o login antes
        fake.online = false

        val error = runCatching { repository.measureClock("ABCD") }.exceptionOrNull()

        assertTrue(error is NoConnection)
    }

    @Test
    fun startIsInLocalClockWithTheMeasuredOffset() = runTest {
        val loaded = listOf(readyPlayer("u1", loadedRound = 1), readyPlayer("bia", loadedRound = 1))
        seedSongRoom(*loaded.toTypedArray(), state = "contagem", round = 1)
        val owner = fake.repository(deviceClock = { now + 2 * MINUTE }) // o do dono adiantado
        val bia = fake.repository("bia", deviceClock = { now - 5 * MINUTE }) // o da bia atrasado

        owner.scheduleStartIfDue("ABCD", 1)

        val startAt = room()!!.getLong("inicio")
        assertEquals(now + 3_000, startAt) // na hora do servidor, apesar do relógio do dono
        val room = bia.observe("ABCD").first()!!
        assertEquals(startAt, room.startAt)
        val clock = bia.measureClock("ABCD")
        assertEquals(now - 5 * MINUTE + 3_000, clock.toLocal(room.startAt!!))
        assertEquals(now, clock.serverNow())
    }

    @Test
    fun backToLobbyClearsStart() = runTest {
        seedSongRoom(readyPlayer("u1"), readyPlayer("bia"), state = "partida", round = 1, startAt = now - 60_000)

        fake.repository().backToLobby("ABCD")

        val write = fake.writes.single()
        assertEquals(setOf("estado", "inicio", "atividadeEm"), patchKeys(write))
        assertTrue(JSONObject(write.body!!).isNull("inicio"))
        assertEquals("sala", room()!!.getString("estado"))
        assertFalse(room()!!.has("inicio"))
        assertEquals(RoomState.LOBBY, observed().state)
    }

    @Test
    fun leftIsWrittenForTheRound() = runTest {
        seedSongRoom(readyPlayer("u1"), readyPlayer("bia"), state = "partida", round = 2, startAt = now)

        fake.repository("bia").markLeft("ABCD", 2)

        assertEquals(setOf("jogadores/bia/saiu"), patchKeys(fake.writes.single()))
        assertEquals(2, players().getJSONObject("bia").getInt("saiu"))
        val room = observed()
        assertEquals(2, room.players.single { it.uid == "bia" }.leftRound)
        assertEquals(listOf("u1"), room.playersInRound().map { it.uid })
    }

    @Test
    fun refusedLoadedIsRetriedOnlyForTheCurrentRound() = runTest {
        seedSongRoom(readyPlayer("u1"), readyPlayer("bia"), state = "contagem", round = 2)
        var refusals = 1
        fake.refuse = { "carregou" in it.body.orEmpty() && refusals-- > 0 }

        fake.repository("bia").markLoaded("ABCD", 2) // recusada uma vez: relê, a rodada é a mesma, refaz

        assertEquals(2, players().getJSONObject("bia").getInt("carregou"))
        fake.refuse = { "saiu" in it.body.orEmpty() } // a rodada já é outra: recusada e não insiste
        val before = fake.writes.size
        fake.repository("bia").markLeft("ABCD", 1)
        assertEquals(before + 1, fake.writes.size)
        assertFalse(players().getJSONObject("bia").has("saiu"))
    }

    @Test
    fun m2WritesWithoutInternetDoNotThrow() = runTest {
        seedSongRoom(readyPlayer("u1"), readyPlayer("bia"), state = "contagem", round = 1)
        val repository = fake.repository()
        fake.online = false

        repository.chooseSong("ABCD", SONG, Difficulty.HARD)
        repository.setMe("ABCD", Difficulty.HARD, ready = true, song = SongStatus.HAS)
        repository.markLoaded("ABCD", 1)
        repository.markLeft("ABCD", 1)
        repository.scheduleStartIfDue("ABCD", 1)
        repository.backToLobby("ABCD")

        assertEquals("contagem", room()!!.getString("estado"))
    }

    @Test
    fun joinAgainKeepsMyDifficultyReadyAndSong() = runTest {
        seedSongRoom(FakePlayer("ana"), readyPlayer("u1", loadedRound = 1), owner = "ana", state = "partida", round = 1)

        assertEquals(JoinResult.Joined("ABCD"), fake.repository().join("ABCD", "Bia"))

        val me = players().getJSONObject("u1")
        assertEquals(
            listOf("HARD", true, "tem"),
            listOf(me.getString("dificuldade"), me.getBoolean("pronto"), me.getString("musica")),
        )
        val written = JSONObject(fake.writes.single().body!!).getJSONObject("jogadores/u1")
        // o carregou/saiu não vão (as regras exigem a rodada atual; numa rodada nova valeriam de outra)
        assertEquals(
            setOf("nome", "aparelho", "vaga", "entrouEm", "vistoEm", "dificuldade", "pronto", "musica"),
            keys(written),
        )
    }

    private companion object {
        const val T0 = 1_800_000_000_000L
        const val MINUTE = 60_000L
        val SONG = RoomSong(
            chartId = "0123456789abcdef0123456789abcdef01234567",
            title = "Música",
            artist = "Banda",
            difficulties = setOf(Difficulty.EXPERT, Difficulty.EASY, Difficulty.HARD),
            durationMs = 215_000,
            encore = EncoreRef(md5 = "0123456789abcdef0123456789abcdef", video = true, charter = "Fulano"),
        )
        const val SONG_JSON = """{"chartId":"0123456789abcdef0123456789abcdef01234567","titulo":"Música",""" +
            """"artista":"Banda","dificuldades":"EASY,HARD,EXPERT","duracaoMs":215000}"""
    }
}
