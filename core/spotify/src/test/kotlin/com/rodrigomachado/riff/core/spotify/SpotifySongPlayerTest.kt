package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.model.SpotifyError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SpotifySongPlayerTest {
    private val remote = FakeSpotifyRemote()
    private var now = 1_000L
    private val ours = "spotify:track:nossa"

    private fun state(position: Long, isPaused: Boolean = false, uri: String = ours, duration: Long = 200_000) =
        PlaybackState(uri, "Música", "Banda", duration, position, isPaused, capturedAtElapsedMs = now)

    private suspend fun TestScope.prepared(): SpotifySongPlayer {
        val player = SpotifySongPlayer(remote, backgroundScope) { now }
        player.prepare(ours)
        runCurrent()
        return player
    }

    private fun TestScope.emit(state: PlaybackState?) {
        remote.playback.value = state
        runCurrent()
    }

    @Test
    fun prepareFailsWhenSpotifyIsUnavailable() = runTest {
        remote.connectTo = SpotifyConnection.Failed(SpotifyError.NOT_INSTALLED)
        val player = SpotifySongPlayer(remote, backgroundScope) { now }

        assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { player.prepare(ours) } }
    }

    @Test
    fun firstPlayStartsTheTrackFromTheBeginningLaterOnesResume() = runTest {
        val player = prepared()

        player.pause() // antes de começar: não mexe na música do usuário
        player.play()
        player.pause()
        player.play()

        assertEquals(listOf("play:$ours", "pause", "resume"), remote.commands)
    }

    @Test
    fun playAndPauseApplyAtOnceBeforeSpotifyConfirms() = runTest {
        val player = prepared()

        player.play()
        assertTrue(player.isPlaying)
        now += 300
        assertEquals(300L, player.positionMs)

        player.pause()
        assertFalse(player.isPlaying)
        now += 5_000
        assertEquals(300L, player.positionMs)
    }

    @Test
    fun positionIsExtrapolatedAndFrozenWhenPaused() = runTest {
        val player = prepared()
        player.play()

        now += 100
        emit(state(position = 10_000))
        now += 500
        assertEquals(10_500L, player.positionMs)

        emit(state(position = 60_000)) // o jogador pulou na barra: vale na hora
        assertEquals(60_000L, player.positionMs)

        emit(state(position = 61_000, isPaused = true))
        now += 2_000
        assertEquals(61_000L, player.positionMs)
        assertFalse(player.isPlaying)
    }

    @Test
    fun statesFromBeforeTheLastCommandAreIgnored() = runTest {
        val player = prepared()
        val beforePlay = state(position = 90_000) // a música estava no meio quando o jogador apertou Jogar
        now += 100
        player.play()

        emit(beforePlay)

        assertEquals(0L, player.positionMs)
    }

    @Test
    fun trackChangeAfterOurTrackCompletesTheSong() = runTest {
        val player = prepared()
        player.play()
        now += 100
        emit(state(position = 1_000, uri = "spotify:track:outra")) // ainda não trocou para a nossa
        assertFalse(player.isCompleted)

        emit(state(position = 2_000))
        emit(state(position = 0, uri = "spotify:track:proxima"))

        assertTrue(player.isCompleted)
    }

    @Test
    fun pausingAtTheEndCompletesTheSong() = runTest {
        val player = prepared()
        player.play()
        now += 100

        emit(state(position = 199_000, isPaused = true, duration = 200_000))

        assertTrue(player.isCompleted)
    }

    @Test
    fun losingTheConnectionPausesAndKeepsThePosition() = runTest {
        val player = prepared()
        player.play()
        now += 100
        emit(state(position = 30_000))
        now += 400

        emit(null)
        now += 10_000

        assertFalse(player.isPlaying)
        assertEquals(30_400L, player.positionMs)
    }

    @Test
    fun rereadsTheStateEveryTwoSeconds() = runTest {
        prepared()

        advanceTimeBy(SpotifySongPlayer.REFRESH_INTERVAL_MS * 2 + 1)

        assertEquals(2, remote.refreshes)
    }

    @Test
    fun leavingMidSongPausesSpotifyButFinishingDoesNot() = runTest {
        val leaving = prepared()
        leaving.play()
        leaving.release()
        assertEquals(listOf("play:$ours", "pause"), remote.commands)

        remote.commands.clear()
        val finished = prepared()
        finished.play()
        now += 100
        emit(state(position = 2_000))
        emit(state(position = 0, uri = "spotify:track:proxima"))
        finished.release()
        assertEquals(listOf("play:$ours"), remote.commands)
    }

    @Test
    fun resumingWhileSpotifyReconnectsStaysPaused() = runTest {
        val player = prepared()
        player.play()
        now += 100
        emit(state(position = 30_000))
        remote.connection.value = SpotifyConnection.Failed(SpotifyError.DISCONNECTED)
        emit(null)

        player.play() // "Continuar" antes de a conexão voltar

        assertFalse(player.isPlaying)
        assertEquals(listOf("play:$ours"), remote.commands)
    }
}
