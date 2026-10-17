package com.rodrigomachado.riff.core.spotify.web

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class SpotifyWebAuthTest {
    private val store = MemoryTokenStore()
    private var now = 1_000_000L
    private val posts = mutableListOf<Map<String, String>>()
    private val answers = ArrayDeque<Pair<Int, String>>()
    private var codeRequests = 0
    private var code: String? = "codigo"

    private fun auth() = PkceSpotifyWebAuth(
        clientId = "cliente",
        redirectUri = "riff://callback",
        store = store,
        post = { _, form ->
            posts += form
            answers.removeFirst()
        },
        launcher = { _, _ ->
            codeRequests++
            code
        },
        clock = { now },
    )

    private fun token(access: String, refresh: String? = null, expiresIn: Int = 3600) = 200 to buildString {
        append("""{"access_token":"$access","token_type":"Bearer","expires_in":$expiresIn""")
        refresh?.let { append(""","refresh_token":"$it"""") }
        append("}")
    }

    @Test
    fun authorizingExchangesTheCodeWithTheVerifierAndKeepsTheRefreshToken() = runTest {
        answers += token("acesso-1", "refresh-1")
        val auth = auth()

        assertTrue(auth.authorize())

        assertEquals(WebAuthState.AUTHORIZED, auth.state.value)
        assertEquals("refresh-1", store.read())
        val exchange = posts.single()
        assertEquals("authorization_code", exchange["grant_type"])
        assertEquals("codigo", exchange["code"])
        assertTrue(exchange["code_verifier"]!!.length >= 43)
        assertEquals("acesso-1", auth.accessToken())
    }

    @Test
    fun aCancelledAuthorizationStaysUnauthorized() = runTest {
        code = null
        val auth = auth()

        assertFalse(auth.authorize())
        assertEquals(WebAuthState.NOT_AUTHORIZED, auth.state.value)
    }

    @Test
    fun anExpiredTokenIsRenewedWithTheRefreshToken() = runTest {
        store.write("refresh-1")
        answers += token("acesso-2", "refresh-2")
        val auth = auth()

        assertEquals("acesso-2", auth.accessToken())

        val renewal = posts.single()
        assertEquals("refresh_token", renewal["grant_type"])
        assertEquals("refresh-1", renewal["refresh_token"])
        assertEquals("refresh-2", store.read())
        assertEquals(0, codeRequests)
    }

    @Test
    fun aValidTokenIsReused() = runTest {
        store.write("refresh-1")
        answers += token("acesso-2")
        val auth = auth()
        auth.accessToken()
        now += 30 * 60_000

        assertEquals("acesso-2", auth.accessToken())
        assertEquals(1, posts.size)
    }

    @Test
    fun aRefusedRenewalLosesTheAuthorizationAndOpensNothing() = runTest {
        store.write("refresh-1")
        answers += 400 to """{"error":"invalid_grant"}"""
        val auth = auth()

        assertNull(auth.accessToken())

        assertEquals(WebAuthState.LOST, auth.state.value)
        assertNull(store.read())
        assertEquals(0, codeRequests)
    }

    @Test
    fun aNetworkFailureKeepsTheAuthorization() = runTest {
        store.write("refresh-1")
        val auth = PkceSpotifyWebAuth(
            "cliente",
            "riff://callback",
            store,
            post = { _, _ -> throw java.io.IOException("sem rede") },
            launcher = { _, _ -> null },
            clock = { now },
        )

        assertNull(auth.accessToken())
        assertEquals(WebAuthState.AUTHORIZED, auth.state.value)
        assertEquals("refresh-1", store.read())
    }

    @Test
    fun onUnauthorizedRenewsOnce() = runTest {
        store.write("refresh-1")
        answers += token("acesso-2")
        answers += token("acesso-3")
        val auth = auth()
        auth.accessToken()

        assertEquals("acesso-3", auth.onUnauthorized())
    }

    @Test
    fun theRefreshTokenNeverGoesToTheLog() = runTest {
        ShadowLog.stream = null
        store.write("refresh-secreto")
        answers += 400 to """{"error":"invalid_grant"}"""

        auth().accessToken()

        assertTrue(ShadowLog.getLogs().none { it.msg.contains("refresh-secreto") })
    }
}

class MemoryTokenStore : TokenStore {
    private var token: String? = null

    override fun read(): String? = token

    override fun write(refreshToken: String?) {
        token = refreshToken
    }
}
