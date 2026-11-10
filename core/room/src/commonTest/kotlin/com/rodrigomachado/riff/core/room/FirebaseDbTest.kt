package com.rodrigomachado.riff.core.room

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class) // o relógio virtual do runTest
class FirebaseDbTest : JsonTest() {
    private var tokens = 0

    /** A resposta do banco; o login anônimo responde sozinho, com um token novo a cada renovação (t1, t2…). */
    private var database: (FakeRequest) -> HttpReply = { ok("null") }
    private val http = FakeHttp { request ->
        when {
            request.url.startsWith("https://identitytoolkit") -> {
                tokens++
                ok("""{"idToken":"t$tokens","refreshToken":"r","expiresIn":"3600","localId":"u1"}""")
            }

            request.url.startsWith("https://securetoken") -> {
                tokens++
                ok("""{"id_token":"t$tokens","refresh_token":"r","expires_in":"3600","user_id":"u1"}""")
            }

            else -> database(request)
        }
    }
    private val db = FirebaseDb(http, AnonymousAuth(http, FakeIdentityStore(), "K", clock = { 0L }), DB_URL)

    private fun dbRequests() = http.requests.filter { it.url.startsWith(DB_URL) }

    @Test
    fun putWithNullEtagReturnsConflictOn412() = runTest {
        database = { HttpReply(412, """{"error":"..."}""", emptyMap()) }

        val result = db.put("salas/ABCD", mapOf("dono" to "u1"), ifMatch = "null_etag")

        assertEquals(PutResult.Conflict, result)
        val request = dbRequests().single()
        assertEquals("PUT", request.method)
        assertEquals("null_etag", request.headers["if-match"])
        assertEquals("true", request.headers["X-Firebase-ETag"])
        assertEquals("""{"dono":"u1"}""", request.body)

        database = { ok("""{"dono":"u1"}""") }
        assertEquals(PutResult.Ok, db.put("salas/ABCD", mapOf("dono" to "u1"), ifMatch = "null_etag"))
    }

    @Test
    fun urlsCarryAuthAndJsonSuffix() = runTest {
        database = { ok("""{"dono":"u1"}""") }

        val room = db.get("/salas/ABCD") as JSONObject
        db.patch("salas/ABCD", mapOf("jogadores/u2" to mapOf("nome" to "Bia"), "vagas/v2" to null))
        db.delete("salas/ABCD")

        assertEquals("u1", room.getString("dono"))
        val requests = dbRequests()
        assertEquals(listOf("GET", "PATCH", "DELETE"), requests.map { it.method })
        requests.forEach { assertEquals("$DB_URL/salas/ABCD.json?auth=t1", it.url) }
        // o nulo do PATCH apaga: precisa ir no corpo
        assertEquals("""{"nome":"Bia"}""", JSONObject(requests[1].body!!).getJSONObject("jogadores/u2").toString())
        assertEquals(true, JSONObject(requests[1].body!!).isNull("vagas/v2"))
        assertEquals(1, tokens) // o token é o mesmo para os três
    }

    @Test
    fun valuesAreWrittenAsJson() = runTest {
        db.put("salas/ABCD/atividadeEm", ServerTime.NOW)
        db.put("salas/ABCD/estado", "sala")
        db.put("salas/ABCD/n", 3)
        db.put("salas/ABCD/lista", listOf(true, null))
        db.put("salas/ABCD/x", null)

        assertEquals(
            listOf("""{".sv":"timestamp"}""", "\"sala\"", "3", "[true,null]", "null"),
            dbRequests().map { it.body },
        )
        assertNull(db.get("salas/ABCD"))
    }

    @Test
    fun etagComesWithTheValue() = runTest {
        database = { ok("\"sala\"", mapOf("etag" to "abc=")) }

        val (etag, value) = db.etag("salas/ABCD/estado")

        assertEquals("abc=", etag)
        assertEquals("sala", value)
        assertEquals("true", dbRequests().single().headers["X-Firebase-ETag"])
    }

    @Test
    fun refusedWriteThrowsTheCode() = runTest {
        database = { HttpReply(401, """{"error":"Permission denied"}""", emptyMap()) }

        try {
            db.patch("salas/ABCD", mapOf("dono" to "u2"))
            fail("o banco recusou")
        } catch (e: RoomHttpError) {
            assertEquals(401, e.code)
        }
    }

    /** Uma resposta ilegível (cortada, um proxy): erro do servidor, nunca um JSONException solto. */
    @Test
    fun unreadableAnswerThrowsRoomHttpError() = runTest {
        database = { ok("""{"dono":""") } // cortada no meio

        for (read in listOf<suspend () -> Unit>({ db.get("salas/ABCD") }, { db.etag("salas/ABCD") })) {
            try {
                read()
                fail("resposta ilegível")
            } catch (e: RoomHttpError) {
                assertEquals(200, e.code)
            }
        }
    }

    @Test
    fun observeAppliesPutsAndPatches() = runTest {
        http.streams += flowOf(
            *(
                sse("put", """{"path":"/","data":{"dono":"u1","jogadores":{"u1":{"nome":"Ana"}}}}""") +
                    sse("keep-alive", "null") +
                    sse("patch", """{"path":"/jogadores/u2","data":{"nome":"Bia"}}""") +
                    sse("put", """{"path":"/jogadores/u1","data":null}""")
                ).toTypedArray(),
        )

        val trees = db.observe("salas/ABCD").take(3).toList().map { it.toString() }

        assertEquals(
            listOf(
                """{"dono":"u1","jogadores":{"u1":{"nome":"Ana"}}}""",
                """{"dono":"u1","jogadores":{"u1":{"nome":"Ana"},"u2":{"nome":"Bia"}}}""",
                """{"dono":"u1","jogadores":{"u2":{"nome":"Bia"}}}""",
            ).map { JSONObject(it).toString() }, // a ordem das chaves do org.json não é a do texto
            trees,
        )
        assertEquals(listOf("$DB_URL/salas/ABCD.json?auth=t1"), http.streamUrls)
    }

    @Test
    fun streamReopensWithFreshTokenOnAuthRevoked() = runTest {
        http.streams += flowOf(
            *(sse("put", """{"path":"/","data":"a"}""") + sse("auth_revoked", "\"credential is no longer valid\""))
                .toTypedArray(),
        )
        http.streams += flowOf(*sse("put", """{"path":"/","data":"b"}""").toTypedArray())

        val values = db.observe("salas/ABCD").take(2).toList()

        assertEquals(listOf("a", "b"), values)
        assertEquals(
            listOf("$DB_URL/salas/ABCD.json?auth=t1", "$DB_URL/salas/ABCD.json?auth=t2"),
            http.streamUrls,
        )
        assertEquals(0L, testScheduler.currentTime) // reabre na hora, sem espera
    }

    /** Revogado de novo logo depois de renovar (sem nenhum evento no meio): espera 1 s, 2 s… em vez de martelar. */
    @Test
    fun repeatedRevokesBackOff() = runTest {
        http.streams += flowOf(*sse("auth_revoked", "\"credential is no longer valid\"").toTypedArray())
        http.streams += flowOf(*sse("cancel", "\"permission denied\"").toTypedArray())
        http.streams += flowOf(*sse("auth_revoked", "\"credential is no longer valid\"").toTypedArray())
        http.streams += flowOf(*sse("put", """{"path":"/","data":"a"}""").toTypedArray())

        val values = db.observe("salas/ABCD").take(1).toList()

        assertEquals(listOf("a"), values)
        assertEquals(4, http.streamUrls.size)
        assertEquals(4, tokens) // renova o token a cada revogação
        assertEquals(1_000L + 2_000L, testScheduler.currentTime) // a 1ª reabre na hora; a 2ª e a 3ª esperam
    }

    @Test
    fun streamReopensAfterDrop() = runTest {
        // cai depois de um evento; a segunda tentativa cai sem nada; a terceira volta
        http.streams += flow {
            sse("put", """{"path":"/","data":"a"}""").forEach { emit(it) }
            throw NoConnection()
        }
        http.streams += flow { throw NoConnection() }
        http.streams += flowOf(*sse("put", """{"path":"/","data":"b"}""").toTypedArray())

        val values = db.observe("salas/ABCD").take(2).toList()

        assertEquals(listOf("a", "b"), values)
        assertEquals(3, http.streamUrls.size)
        assertEquals(1_000L + 2_000L, testScheduler.currentTime) // espera 1 s e depois 2 s
    }

    @Test
    fun reconnectWaitDoublesUpTo30s() = runTest {
        repeat(7) { http.streams += flow { throw NoConnection() } }
        http.streams += flow { throw RoomHttpError(503) } // erro do servidor também espera
        http.streams += flow { } // o servidor fechou a conexão: reabre também
        http.streams += flowOf(*sse("put", """{"path":"/","data":1}""").toTypedArray())

        val values = db.observe("salas/ABCD").take(1).toList()

        assertEquals(listOf<Any?>(1), values)
        val waits = listOf(1, 2, 4, 8, 16, 30, 30, 30, 30).sumOf { it * 1_000L }
        assertEquals(waits, testScheduler.currentTime)
    }

    private companion object {
        const val DB_URL = "https://riff-teste.firebaseio.com"
    }
}
