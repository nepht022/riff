package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.model.SpotifyError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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

    /**
     * A música já tocava (1:30) quando a partida começou: o Spotify ainda manda a posição antiga depois do comando
     * de tocar do começo. Aceitar faria o relógio pular para 1:30 e voltar, com as notas até ali já perdidas.
     */
    @Test
    fun theOldPositionOfASongAlreadyPlayingIsIgnoredAtTheStart() = runTest {
        val player = prepared()

        player.play()
        now += 200
        emit(state(position = 90_000)) // atrasado: ainda é a música antes de recomeçar
        assertTrue(player.isStarting)
        assertEquals(0L, player.positionMs)

        now += 300
        emit(state(position = 150)) // recomeçou
        assertFalse(player.isStarting)
        assertEquals(150L, player.positionMs)
    }

    /**
     * Registro no S25: o Spotify avisa "tocando em 0" antes de o áudio andar; aceitar fazia a pista correr ~100 ms
     * na frente e voltar. A partida só começa quando a posição avança.
     */
    @Test
    fun theStartWaitsUntilSpotifyActuallyMoves() = runTest {
        val player = prepared()
        player.play()

        now += 30
        emit(state(position = 0))
        now += 100
        emit(state(position = 0)) // ainda parado
        assertTrue(player.isStarting)
        assertEquals(0L, player.positionMs)

        now += 7
        emit(state(position = 4))
        assertFalse(player.isStarting)
        now += 40
        assertEquals(44L, player.positionMs)
    }

    /** Registro no S25, retomada em 5.631: "tocando em 5.631" e, 73 ms depois, só 5.646. */
    @Test
    fun resumingWaitsUntilSpotifyActuallyMoves() = runTest {
        val player = prepared()
        player.play()
        emit(state(position = 0)) // o Spotify recomeçou a nossa faixa
        emit(state(position = 5_584))
        now += 47
        player.pause() // o jogo para em 5.631
        now += 286
        emit(state(position = 5_640, isPaused = true))

        now += 970
        player.play()
        now += 34
        emit(state(position = 5_631, isPaused = true))
        now += 29
        emit(state(position = 5_631)) // "tocando", mas ainda parado
        now += 73
        assertTrue(player.isStarting)
        assertEquals(5_631L, player.positionMs)

        emit(state(position = 5_646))
        assertFalse(player.isStarting)
        assertEquals(5_646L, player.positionMs)
    }

    /** Logo depois de o jogo pausar, um "tocando" atrasado não é retomada de fora (o menu de pausa piscava). */
    @Test
    fun aLatePlayingStateRightAfterPausingIsIgnored() = runTest {
        val player = prepared()
        player.play()
        emit(state(position = 0)) // o Spotify recomeçou a nossa faixa
        emit(state(position = 30_000))
        now += 500
        player.pause() // o jogo para em 30.500

        now += 100
        emit(state(position = 30_600)) // o Spotify ainda não obedeceu
        assertFalse(player.isPlaying)
        assertEquals(30_500L, player.positionMs)

        now += 200
        emit(state(position = 30_700, isPaused = true)) // obedeceu
        now += 3_000
        emit(state(position = 30_700)) // agora sim: alguém retomou pela notificação
        assertTrue(player.isPlaying)
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
        emit(state(position = 0)) // o Spotify recomeçou a nossa faixa
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
        emit(state(position = 0)) // o Spotify recomeçou a nossa faixa
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
        emit(state(position = 0)) // o Spotify recomeçou a nossa faixa
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
        emit(state(position = 0)) // o Spotify recomeçou a nossa faixa
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
        emit(state(position = 0)) // o Spotify recomeçou a nossa faixa
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

        emit(state(position = 0)) // o Spotify recomeçou a nossa faixa
        emit(state(position = 2_000))
        emit(state(position = 0, uri = "spotify:track:proxima"))

        assertTrue(player.isCompleted)
    }

    @Test
    fun pausingAtTheEndCompletesTheSong() = runTest {
        val player = prepared()
        player.play()
        now += 100

        emit(state(position = 0)) // o Spotify recomeçou a nossa faixa
        emit(state(position = 199_000, isPaused = true, duration = 200_000))

        assertTrue(player.isCompleted)
    }

    @Test
    fun losingTheConnectionPausesAndKeepsThePosition() = runTest {
        val player = prepared()
        player.play()
        now += 100
        emit(state(position = 0)) // o Spotify recomeçou a nossa faixa
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

    /** Fim das notas no outro, "Ir direto para a próxima": a música segue para o Spotify passar para a próxima. */
    @Test
    fun releasingKeepingTheMusicDoesNotPauseSpotify() = runTest {
        val player = prepared()
        player.play()
        now += 100
        emit(state(position = 0)) // o Spotify recomeçou a nossa faixa
        emit(state(position = 180_000))
        remote.commands.clear()

        player.releaseKeepingMusic()

        assertEquals(emptyList<String>(), remote.commands)
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
        emit(state(position = 0)) // o Spotify recomeçou a nossa faixa
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
        emit(state(position = 0)) // o Spotify recomeçou a nossa faixa
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
        emit(state(position = 0)) // o Spotify recomeçou a nossa faixa
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
        emit(state(position = 0)) // o Spotify recomeçou a nossa faixa
        emit(state(position = 30_000))
        loseConnection()

        reconnect(state(position = 5_000, isPaused = true, uri = "spotify:track:outra"))

        assertFalse(player.isCompleted)
    }

    /** Pela sessão de mídia não há como pôr outra faixa no lugar sem trocar a playlist: espera o jogador voltar. */
    @Test
    fun resumingAfterTheSessionCameBackOnAnotherTrackWaitsAndAsksToGoBack() = runTest {
        val player = prepared()
        player.play()
        now += 100
        emit(state(position = 0)) // o Spotify recomeçou a nossa faixa
        emit(state(position = 30_000))
        now += 400
        loseConnection()
        reconnect(state(position = 5_000, isPaused = true, uri = "spotify:track:outra"))
        remote.commands.clear()

        player.play()

        assertEquals(emptyList<String>(), remote.commands)
        assertFalse(player.isPlaying)
        assertTrue(player.trackRefused)
    }

    @Test
    fun resumingAfterReconnectingOnOurTrackSeeksBackAndWaitsForSpotify() = runTest {
        val player = prepared()
        player.play()
        now += 100
        emit(state(position = 0)) // o Spotify recomeçou a nossa faixa
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

    private fun TestScope.playOurs(player: SpotifySongPlayer, at: Long = 30_000) {
        player.play()
        now += 100
        emit(state(position = 0)) // o Spotify recomeçou a nossa faixa
        emit(state(position = at))
    }

    /** Conta grátis: anúncio no meio da música. A pista para e volta sozinha com a música, sem acabar a partida. */
    @Test
    fun anAdMidSongPausesAndResumesWithoutEnding() = runTest {
        val player = prepared()
        playOurs(player)
        now += 500
        remote.adPlaying.value = true
        emit(null)
        now += 15_000
        assertEquals(30_500L, player.positionMs)
        assertFalse(player.isCompleted)

        remote.adPlaying.value = false
        emit(state(position = 30_500))
        now += 1_000
        assertEquals(31_500L, player.positionMs)
        assertTrue(player.isPlaying)
    }

    /** Conta grátis no modo contínuo: o anúncio vem entre as músicas; a seguinte depois dele acaba a partida. */
    @Test
    fun anAdBetweenSongsStillEndsTheMatch() = runTest {
        val player = prepared()
        playOurs(player, at = 199_000)
        remote.adPlaying.value = true
        emit(null)
        remote.adPlaying.value = false
        emit(state(position = 0, uri = "spotify:track:proxima"))

        assertTrue(player.isCompleted)
    }

    /** A permissão caiu (ou o Spotify fechou): a pista congela e só volta pelo Continuar. */
    @Test
    fun losingTheSessionFreezesTheTrack() = runTest {
        val player = prepared()
        playOurs(player)
        now += 400
        emit(null)
        now += 5_000
        assertEquals(30_400L, player.positionMs)
        emit(state(position = 30_400, isPaused = true))
        assertFalse(player.isPlaying)
    }

    @Test
    fun resumingAfterTheSessionCameBackSeeksTheSameTrack() = runTest {
        val player = prepared()
        playOurs(player)
        now += 400
        emit(null)
        emit(state(position = 30_300, isPaused = true))
        remote.commands.clear()

        player.play()

        assertEquals(listOf("seek:30400", "resume"), remote.commands)
    }

    /** "Jogar de novo" sem conseguir voltar a música (conta grátis): a pausa pede para voltar no Spotify. */
    @Test
    fun aTrackThatNeverStartsIsRefused() = runTest {
        val player = prepared()
        player.play()
        assertFalse(player.trackRefused)

        now += SpotifySongPlayer.START_TIMEOUT_MS + SpotifySongPlayer.REFRESH_INTERVAL_MS
        advanceTimeBy(SpotifySongPlayer.REFRESH_INTERVAL_MS + 1)

        assertTrue(player.trackRefused)
        assertFalse(player.isPlaying)
        player.play()
        assertFalse(player.trackRefused) // tenta de novo
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
        emit(state(position = 0)) // o Spotify recomeçou a nossa faixa
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

    @Test
    fun reconnectingBeforeOurTrackEverStartedPlaysItFromTheStartWithoutAForcedPause() = runTest {
        val player = prepared()
        player.play()
        emit(state(position = 4_000, uri = "spotify:track:outra")) // o Spotify ainda está na música anterior
        loseConnection() // e a conexão cai antes de ele começar a nossa
        reconnect(state(position = 5_000, isPaused = true, uri = "spotify:track:outra"))
        remote.commands.clear()
        now += 10_000

        player.play()
        assertEquals(listOf("play:$ours"), remote.commands) // nunca começou: toca do começo
        now += 2_000
        advanceTimeBy(SpotifySongPlayer.REFRESH_INTERVAL_MS + 1)

        assertTrue(player.isPlaying) // o prazo de 5 s conta desta retomada
    }

    @Test
    fun leavingWhileSpotifyIsStillConnectingStopsEverything() = runTest {
        remote.connectTo = SpotifyConnection.Connecting
        val player = SpotifySongPlayer(remote, backgroundScope) { now }
        launch { runCatching { player.prepare(ours) } }
        runCurrent()

        player.release()
        remote.connection.value = SpotifyConnection.Connected
        runCurrent()
        advanceTimeBy(10_000)

        assertEquals(0, remote.refreshes) // nada fica consultando o Spotify depois de sair da partida
    }
}
