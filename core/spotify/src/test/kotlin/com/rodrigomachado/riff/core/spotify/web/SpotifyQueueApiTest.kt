package com.rodrigomachado.riff.core.spotify.web

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpotifyQueueApiTest {
    private val auth = FakeWebAuth()
    private val answers = ArrayDeque<HttpAnswer>()
    private val calls = mutableListOf<Pair<String, String>>()
    private val api = SpotifyQueueApi(auth) { url, token ->
        calls += url to token
        answers.removeFirst()
    }

    private fun track(uri: String, name: String, artists: List<String>, local: Boolean = false) = """
        {"type":"track","uri":"$uri","name":"$name","duration_ms":200000,"is_local":$local,
         "artists":[${artists.joinToString(",") { """{"name":"$it"}""" }}]}
    """.trimIndent()

    private val episode = """{"type":"episode","uri":"spotify:episode:p","name":"Podcast","duration_ms":3600000}"""

    private fun queue(vararg items: String) = HttpAnswer(
        200,
        """{"currently_playing":${track(
            "spotify:track:now",
            "Agora",
            listOf("A"),
        )},"queue":[${items.joinToString(",")}]}""",
    )

    @Test
    fun theNextTwoMusicTracksAreRead() = runTest {
        answers += queue(
            episode,
            track("spotify:track:local", "Local", listOf("Eu"), local = true),
            track("spotify:track:1", "Um", listOf("Banda", "Convidado")),
            track("spotify:track:2", "Dois", listOf("Banda")),
            track("spotify:track:3", "Três", listOf("Banda")),
        )

        val read = api.next()

        assertEquals(
            QueueRead.Tracks(
                listOf(
                    QueuedTrack("spotify:track:1", "Um", "Banda, Convidado", 200_000),
                    QueuedTrack("spotify:track:2", "Dois", "Banda", 200_000),
                ),
            ),
            read,
        )
        assertEquals("https://api.spotify.com/v1/me/player/queue" to "token-1", calls.single())
    }

    @Test
    fun a401RenewsOnceAndRetries() = runTest {
        answers += HttpAnswer(401, "")
        answers += queue(track("spotify:track:1", "Um", listOf("Banda")))

        val read = api.next()

        assertEquals(1, (read as QueueRead.Tracks).tracks.size)
        assertEquals(listOf("token-1", "token-2"), calls.map { it.second })
    }

    @Test
    fun a401AfterRenewingIsUnavailable() = runTest {
        answers += HttpAnswer(401, "")
        answers += HttpAnswer(401, "")

        assertEquals(QueueRead.Unavailable, api.next())
        assertEquals(2, calls.size)
    }

    @Test
    fun a429ReturnsRetryAfter() = runTest {
        answers += HttpAnswer(429, "", retryAfterSeconds = 30)

        assertEquals(QueueRead.RetryAfter(30), api.next())
    }

    @Test
    fun noTokenIsUnavailableWithoutCallingTheApi() = runTest {
        auth.token = null

        assertEquals(QueueRead.Unavailable, api.next())
        assertEquals(0, calls.size)
    }

    @Test
    fun aNetworkFailureIsUnavailable() = runTest {
        val failing = SpotifyQueueApi(auth) { _, _ -> throw java.io.IOException("sem rede") }

        assertEquals(QueueRead.Unavailable, failing.next())
    }
}

class FakeWebAuth : SpotifyWebAuth {
    override val state = MutableStateFlow(WebAuthState.AUTHORIZED)
    var token: String? = "token-1"
    var authorizeResult = true
    var authorizations = 0

    override suspend fun authorize(): Boolean {
        authorizations++
        if (authorizeResult) state.value = WebAuthState.AUTHORIZED
        return authorizeResult
    }

    override suspend fun accessToken(): String? = token

    override suspend fun onUnauthorized(): String? {
        token = "token-2"
        return token
    }
}
