package com.rodrigomachado.riff.core.spotify.session

import android.media.session.PlaybackState.STATE_PAUSED
import android.media.session.PlaybackState.STATE_PLAYING
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.model.SpotifyError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaSessionSpotifyTest {
    private val session = FakeSpotifySession()

    private fun TestScope.remote() = MediaSessionSpotify(session, correctionMs = 0, scope = backgroundScope)

    private fun playing(uri: String, positionMs: Long = 30_000, at: Long = 0) =
        SessionSnapshot(uri, "T", "A", 200_000, STATE_PLAYING, positionMs, at, 1f, isAd = false)

    @Test
    fun withoutPermissionTheConnectionSaysSo() = runTest {
        session.hasPermission = false
        val remote = remote()
        remote.connect()
        assertEquals(SpotifyConnection.Failed(SpotifyError.NO_PERMISSION), remote.connection.value)
    }

    @Test
    fun withoutSpotifyInstalledTheConnectionSaysSo() = runTest {
        session.isInstalled = false
        val remote = remote()
        remote.connect()
        assertEquals(SpotifyConnection.Failed(SpotifyError.NOT_INSTALLED), remote.connection.value)
    }

    @Test
    fun withPermissionItIsConnectedEvenWithNothingPlaying() = runTest {
        val remote = remote()
        remote.connect()
        runCurrent()
        assertEquals(SpotifyConnection.Connected, remote.connection.value)
        assertNull(remote.playback.value)
        assertTrue(session.rechecks > 0)
    }

    @Test
    fun theSessionStateIsThePlayback() = runTest {
        val remote = remote()
        remote.connect()
        session.snapshot.value = playing("spotify:track:1", 5_000, at = 100)
        runCurrent()
        assertEquals("spotify:track:1", remote.playback.value!!.trackUri)
        assertEquals(100L, remote.playback.value!!.capturedAtElapsedMs)
    }

    @Test
    fun anAdIsFlaggedAndIsNoPlayback() = runTest {
        val remote = remote()
        remote.connect()
        session.snapshot.value = playing("spotify:track:1").copy(isAd = true)
        runCurrent()
        assertTrue(remote.adPlaying.value)
        assertNull(remote.playback.value)
        session.snapshot.value = playing("spotify:track:1")
        runCurrent()
        assertFalse(remote.adPlaying.value)
    }

    @Test
    fun losingThePermissionFailsTheConnection() = runTest {
        val remote = remote()
        remote.connect()
        session.snapshot.value = playing("spotify:track:1")
        runCurrent()
        session.hasPermission = false
        remote.refresh()
        runCurrent()
        assertEquals(SpotifyConnection.Failed(SpotifyError.NO_PERMISSION), remote.connection.value)
        assertNull(remote.playback.value)
    }

    @Test
    fun playingTheCurrentTrackRestartsIt() = runTest {
        val remote = remote()
        remote.connect()
        session.snapshot.value = playing("spotify:track:1")
        runCurrent()
        remote.play("spotify:track:1")
        runCurrent()
        assertEquals(listOf("seekTo:0", "play"), session.commands)
    }

    /**
     * "Jogar de novo": o Spotify já passou para a seguinte. Do começo dela, um ⏮ sempre vai para a anterior (a posição
     * da sessão fica parada entre os avisos e não serve para decidir), mantendo a playlist e a fila.
     */
    @Test
    fun goingBackRestartsTheCurrentThenGoesToThePrevious() = runTest {
        val remote = remote()
        remote.connect()
        session.snapshot.value = playing("spotify:track:2", positionMs = 0, at = 0) // parada desde o começo dela
        runCurrent()

        remote.play("spotify:track:1")
        runCurrent()
        session.snapshot.value = playing("spotify:track:1", positionMs = 0)
        advanceTimeBy(5_000)

        assertEquals(listOf("seekTo:0", "previous"), session.commands)
    }

    /** Chegou pausada (o Spotify às vezes para no ⏮): manda tocar. */
    @Test
    fun arrivingPausedIsResumed() = runTest {
        val remote = remote()
        remote.connect()
        session.snapshot.value = playing("spotify:track:2")
        runCurrent()

        remote.play("spotify:track:1")
        runCurrent()
        session.snapshot.value = playing("spotify:track:1", positionMs = 0).copy(state = STATE_PAUSED)
        advanceTimeBy(5_000)

        assertEquals(listOf("seekTo:0", "previous", "play"), session.commands)
    }

    @Test
    fun aTrackThatIsNotThereSendsNothingMore() = runTest {
        val remote = remote()
        remote.connect()
        session.snapshot.value = playing("spotify:track:2")
        runCurrent()
        remote.play("spotify:track:1")
        advanceTimeBy(10_000)
        assertEquals(listOf("seekTo:0", "previous"), session.commands)
    }

    /** Saiu (ou pausou) enquanto o ⏮ esperava: nada mais é mandado ao Spotify. */
    @Test
    fun leavingStopsGoingBack() = runTest {
        val remote = remote()
        remote.connect()
        session.snapshot.value = playing("spotify:track:2")
        runCurrent()
        remote.play("spotify:track:1")
        runCurrent()

        remote.cancelPendingCommands()
        session.snapshot.value = playing("spotify:track:1", positionMs = 0).copy(state = STATE_PAUSED)
        advanceTimeBy(5_000)

        assertEquals(listOf("seekTo:0", "previous"), session.commands)
    }

    @Test
    fun commandsGoToTheSession() = runTest {
        val remote = remote()
        remote.connect()
        remote.pause()
        remote.resume()
        remote.seekTo(1_234)
        remote.skipNext()
        remote.skipPrevious()
        assertEquals(listOf("pause", "play", "seekTo:1234", "next", "previous"), session.commands)
    }
}
