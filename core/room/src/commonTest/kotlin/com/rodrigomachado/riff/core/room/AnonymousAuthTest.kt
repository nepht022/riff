package com.rodrigomachado.riff.core.room

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AnonymousAuthTest : JsonTest() {
    private var now = 1_000_000L
    private val store = FakeIdentityStore()
    private val http = FakeHttp { request ->
        if (request.url.startsWith(SIGN_UP)) {
            ok("""{"idToken":"id-novo","refreshToken":"r1","expiresIn":"3600","localId":"u1"}""")
        } else {
            refreshes++
            ok("""{"id_token":"id$refreshes","refresh_token":"r${refreshes + 1}","expires_in":"3600","user_id":"u1"}""")
        }
    }
    private var refreshes = 0
    private val auth = AnonymousAuth(http, store, "K", clock = { now })

    @Test
    fun firstUseSignsUpAndStoresIdentity() = runTest {
        assertEquals("id-novo", auth.token())
        assertEquals("u1", auth.uid())

        val request = http.requests.single()
        assertEquals("POST", request.method)
        assertEquals("$SIGN_UP?key=K", request.url)
        assertTrue(JSONObject(request.body!!).getBoolean("returnSecureToken"))
        assertEquals(RoomIdentity("u1", "r1"), store.identity)
    }

    @Test
    fun secondUseRefreshesWithStoredToken() = runTest {
        store.identity = RoomIdentity("u1", "r0")

        assertEquals("id1", auth.token())

        val request = http.requests.single()
        assertEquals("$REFRESH?key=K", request.url)
        assertEquals("grant_type=refresh_token&refresh_token=r0", request.body)
        assertEquals("application/x-www-form-urlencoded", request.headers["Content-Type"])
        // o Google pode trocar o refreshToken: o novo fica guardado
        assertEquals(RoomIdentity("u1", "r2"), store.identity)
        assertEquals("u1", auth.uid())
    }

    @Test
    fun tokenIsReusedUntilFiveMinutesBeforeExpiry() = runTest {
        val start = now
        auth.token()

        now = start + 3_600_000 - 300_001
        assertEquals("id-novo", auth.token())
        assertEquals(1, http.requests.size)

        now = start + 3_600_000 - 300_000
        assertEquals("id1", auth.token())
        assertEquals(2, http.requests.size)
        assertTrue(http.requests.last().url.startsWith(REFRESH))
    }

    @Test
    fun refreshNowRenewsEvenAFreshToken() = runTest {
        auth.token()

        auth.refreshNow()

        assertEquals("id1", auth.token())
        assertEquals(2, http.requests.size)
    }

    @Test
    fun lostAccountSignsUpAgain() = runTest {
        store.identity = RoomIdentity("u0", "r-apagado")
        val google = http.answer
        http.answer = { if (it.url.startsWith(REFRESH)) HttpReply(400, "{}", emptyMap()) else google(it) }

        assertEquals("id-novo", auth.token())

        assertEquals(RoomIdentity("u1", "r1"), store.identity)
    }

    @Test
    fun noInternetThrowsNoConnection() = runTest {
        http.answer = { throw NoConnection() }

        try {
            auth.token()
            fail("sem internet não há token")
        } catch (_: NoConnection) {
            // esperado
        }
    }

    @Test
    fun serverErrorThrowsTheCode() = runTest {
        http.answer = { HttpReply(503, "", emptyMap()) }

        try {
            auth.token()
            fail("o Google fora do ar não dá token")
        } catch (e: RoomHttpError) {
            assertEquals(503, e.code)
        }
    }

    /** Um 200 sem os campos esperados (proxy, mudança da API) é erro do Google, não um JSONException solto. */
    @Test
    fun answerWithoutFieldsThrowsRoomHttpError() = runTest {
        http.answer = { ok("{}") }
        try {
            auth.uid()
            fail("sem localId não há uid")
        } catch (e: RoomHttpError) {
            assertEquals(200, e.code)
        }

        store.identity = RoomIdentity("u1", "r0")
        try {
            auth.token()
            fail("sem id_token não há token")
        } catch (e: RoomHttpError) {
            assertEquals(200, e.code)
        }
    }

    private companion object {
        const val SIGN_UP = "https://identitytoolkit.googleapis.com/v1/accounts:signUp"
        const val REFRESH = "https://securetoken.googleapis.com/v1/token"
    }
}
