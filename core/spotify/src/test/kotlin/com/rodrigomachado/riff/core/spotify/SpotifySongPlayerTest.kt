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
        emit(state(position = 0)) // o Spotify começou a nossa faixa
        player.pause()
        player.play()

        assertEquals(listOf("play:$ours", "pause", "seek:0", "resume"), remote.commands)
    }

    @Test
    fun afterPlayThePositionWaitsUntilSpotifyConfirms() = runTest {
        val player = prepared()

        player.play()
        assertTrue(player.isPlaying) // para o jogo não é uma pausa
        assertTrue(player.isStarting)
        now += 300
        assertEquals(0L, player.positionMs) // o Spotify ainda não começou: a pista espera

        emit(state(position = 40))
        assertFalse(player.isStarting)
        now += 100
        assertEquals(140L, player.positionMs)

        player.pause()
        assertFalse(player.isPlaying)
        now += 5_000
        assertEquals(140L, player.positionMs)
    }

    @Test
    fun whilePausedTheLateStateFromSpotifyDoesNotMoveTheHighway() = runTest {
        val player = prepared()
        player.play()
        emit(state(position = 30_000))
        now += 500
        player.pause() // o jogo para em 30.500

        now += 200
        emit(state(position = 30_670, isPaused = true)) // o Spotify obedeceu mais tarde

        assertEquals(30_500L, player.positionMs)
        assertFalse(player.isPlaying)
    }

    @Test
    fun resumingPutsSpotifyBackWhereTheGameStoppedAndWaitsForIt() = runTest {
        val player = prepared()
        player.play()
        emit(state(position = 30_000))
        now += 500
        player.pause()
        now += 200
        emit(state(position = 30_670, isPaused = true))
        remote.commands.clear()

        now += 3_000
        player.play()

        assertEquals(listOf("seek:30500", "resume"), remote.commands)
        assertTrue(player.isStarting)
        now += 250
        assertEquals(30_500L, player.positionMs)
        emit(state(position = 30_510))
        now += 100
        assertEquals(30_610L, player.positionMs)
    }

    @Test
    fun resumingSeeksBackEvenIfSpotifyNeverReportedThePause() = runTest {
        val player = prepared()
        player.play()
        emit(state(position = 30_000))
        now += 500
        player.pause()
        remote.commands.clear()

        now += 1_000
        player.play()

        assertEquals(listOf("seek:30500", "resume"), remote.commands)
    }

    @Test
    fun spotifyThatNeverResumesPausesTheGame() = runTest {
        val player = prepared()
        player.play()
        emit(state(position = 30_000))
        player.pause()
        player.play()

        now += SpotifySongPlayer.START_TIMEOUT_MS + 1
        advanceTimeBy(SpotifySongPlayer.REFRESH_INTERVAL_MS * 3)
        runCurrent()

        assertFalse(player.isStarting)
        assertFalse(player.isPlaying)
        assertEquals(30_000L, player.positionMs)
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

    private fun TestScope.loseConnection(error: SpotifyError = SpotifyError.DISCONNECTED) {
        remote.connection.value = SpotifyConnection.Failed(error)
        emit(null)
    }

    private fun TestScope.reconnect(state: PlaybackState) {
        remote.connection.value = SpotifyConnection.Connected
        emit(state)
    }

    @Test
    fun afterReconnectingTheGameKeepsItsPositionUntilResumed() = runTest {
        val player = prepared()
        player.play()
        now += 100
        emit(state(position = 30_000))
        now += 400
        loseConnection()

        reconnect(state(position = 0, isPaused = true)) // o Spotify voltou do zero

        assertEquals(30_400L, player.positionMs)
        assertFalse(player.isPlaying)
    }

    @Test
    fun spotifyComingBackOnAnotherTrackDoesNotEndTheGame() = runTest {
        val player = prepared()
        player.play()
        now += 100
        emit(state(position = 30_000))
        loseConnection()

        reconnect(state(position = 5_000, isPaused = true, uri = "spotify:track:outra"))

        assertFalse(player.isCompleted)
    }

    @Test
    fun resumingAfterReconnectingPutsOurTrackBackWhereTheGameStopped() = runTest {
        val player = prepared()
        player.play()
        now += 100
        emit(state(position = 30_000))
        now += 400
        loseConnection()
        reconnect(state(position = 5_000, isPaused = true, uri = "spotify:track:outra"))
        remote.commands.clear()

        player.play()

        assertEquals(listOf("play:$ours", "seek:30400"), remote.commands)
        assertTrue(player.isPlaying)
        assertTrue(player.isStarting)
    }

    @Test
    fun resumingAfterReconnectingOnOurTrackSeeksBackAndWaitsForSpotify() = runTest {
        val player = prepared()
        player.play()
        now += 100
        emit(state(position = 30_000))
        now += 400
        loseConnection()
        reconnect(state(position = 30_300, isPaused = true))
        remote.commands.clear()

        player.play()

        assertEquals(listOf("seek:30400", "resume"), remote.commands)
        assertTrue(player.isStarting) // como numa retomada normal: a pista espera o Spotify voltar
        now += 300
        assertEquals(30_400L, player.positionMs)
        emit(state(position = 30_410))
        assertFalse(player.isStarting)
    }

    @Test
    fun resumingAfterAnUnrecoverableDropAsksToConnectAgain() = runTest {
        val player = prepared()
        player.play()
        now += 100
        emit(state(position = 1_000))
        loseConnection(SpotifyError.UNKNOWN)
        val connectsBefore = remote.connects
        remote.connectTo = SpotifyConnection.Connecting

        player.play()

        assertEquals(connectsBefore + 1, remote.connects)
        assertFalse(player.isPlaying)
    }

    @Test
    fun ourTrackNeverStartingPausesTheGame() = runTest {
        val player = prepared()
        player.play() // o Spotify recusou ou não achou a faixa: nenhum estado dela chega

        now += SpotifySongPlayer.START_TIMEOUT_MS + SpotifySongPlayer.REFRESH_INTERVAL_MS
        advanceTimeBy(SpotifySongPlayer.REFRESH_INTERVAL_MS + 1)

        assertFalse(player.isPlaying)
        remote.commands.clear()
        player.play() // "Continuar" tenta de novo do começo
        assertEquals(listOf("play:$ours"), remote.commands)
    }

    @Test
    fun stoppingAtTheStartOfOurTrackAfterItsEndCompletesTheSong() = runTest {
        val player = prepared()
        player.play()
        now += 100
        emit(state(position = 198_500, duration = 200_000))

        now += 2_000
        emit(state(position = 0, isPaused = true, duration = 200_000)) // sem autoplay: volta ao começo parado

        assertTrue(player.isCompleted)
    }

    @Test
    fun pausingBeforeOurTrackStartsAndResumingLaterStartsItAgainWithoutAForcedPause() = runTest {
        val player = prepared()
        player.play()
        now += 1_000
        player.pause() // pausou antes de o Spotify começar a nossa faixa
        remote.commands.clear()
        now += 10_000

        player.play()
        assertEquals(listOf("play:$ours"), remote.commands) // nunca começou: toca do começo de novo
        now += 2_000
        advanceTimeBy(SpotifySongPlayer.REFRESH_INTERVAL_MS + 1)

        assertTrue(player.isPlaying) // o prazo de 5 s conta da retomada, não do primeiro play
    }
}
