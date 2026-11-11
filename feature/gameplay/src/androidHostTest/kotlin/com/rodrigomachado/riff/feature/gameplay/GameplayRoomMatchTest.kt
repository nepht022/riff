package com.rodrigomachado.riff.feature.gameplay

import com.rodrigomachado.riff.core.game.TimeSource
import kotlinx.coroutines.CompletableDeferred
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

/** A partida de sala: carrega, espera o começo combinado, conta 3-2-1, começa junto e não pausa (Decisão 5). */
@OptIn(ExperimentalCoroutinesApi::class)
class GameplayRoomMatchTest : GameplayViewModelTestBase() {
    /** O começo da sala de mentira: o teste decide quando e em que instante a partida começa. */
    private inner class FakeMatchStart : MatchStart {
        var loadedCalls = 0
        var leftCalls = 0
        var finishedCalls = 0
        var playingWhenLoaded: Boolean? = null
        var preparedWhenLoaded: String? = null
        var warmUpsWhenLoaded: Int? = null
        val start = CompletableDeferred<Long>()

        override suspend fun loaded() {
            loadedCalls++
            playingWhenLoaded = player.isPlaying
            preparedWhenLoaded = player.preparedUri
            warmUpsWhenLoaded = player.warmUps
        }

        override suspend fun awaitStart(): Long = start.await()

        override suspend fun left() {
            leftCalls++
        }

        override suspend fun finished() {
            finishedCalls++
        }
    }

    private val match = FakeMatchStart()

    /** O relógio da partida é o tempo virtual do teste: o `delay` e o `nowMs` andam juntos. */
    private fun TestScope.clock() = TimeSource { testScheduler.currentTime }

    private fun GameplayViewModel.ready() = uiState.value as GameplayUiState.Ready

    @Test
    fun roomMatchWaitsForTheAgreedStart() = runTest {
        loader.result = { playable() }
        val viewModel = roomViewModel(match, clock())
        assertEquals(listOf("ABCD" to 3), matchStartsAsked) // o código e a rodada vêm da rota

        // Montada e parada, esperando os outros.
        assertTrue(viewModel.ready().inRoom)
        assertTrue(viewModel.ready().waitingForStart)
        assertNull(viewModel.ready().countdownEndsAt)
        assertFalse(player.isPlaying)

        match.start.complete(5_000)
        runCurrent()
        assertEquals(5_000L, viewModel.ready().countdownEndsAt)
        assertFalse(viewModel.ready().waitingForStart)

        advanceTimeBy(4_999)
        assertFalse(player.isPlaying) // nem um quadro antes
        advanceTimeBy(1)
        runCurrent()
        assertTrue(player.isPlaying) // no instante combinado
        assertEquals(5_000L, testScheduler.currentTime)
    }

    @Test
    fun aStartAlreadyPassedStartsRightAway() = runTest {
        loader.result = { playable() }
        advanceTimeBy(10_000)
        val viewModel = roomViewModel(match, clock())

        match.start.complete(9_000)
        runCurrent()

        assertTrue(player.isPlaying)
        assertFalse(viewModel.ready().isPaused)
    }

    /**
     * Carregou 5 s depois do começo combinado (o celular mais lento): a música e as notas começam em 5 s, juntas com
     * as dos outros, em vez de começar do zero fora de sincronia. As notas que já passaram contam como perdidas, sem
     * o som de erro.
     */
    @Test
    fun aLateLoadStartsAtTheSongPositionOfTheOthers() = runTest {
        loader.result = { playable() }
        advanceTimeBy(10_000)
        val viewModel = roomViewModel(match, clock())

        match.start.complete(5_000)
        runCurrent()

        assertTrue(player.isPlaying)
        assertEquals(listOf(5_000L), player.seeks)
        val session = viewModel.ready().session
        val songTime = session.tick()
        assertTrue("tempo da música $songTime", songTime in 4_900L..5_100L) // com o ajuste de áudio (-40 ms)
        assertEquals(2, session.engine.count(com.rodrigomachado.riff.core.model.Judgment.MISS))
        assertEquals(0, sfx.misses)
    }

    /** Mais de 10 s de atraso: começa assim mesmo, na posição certa. */
    @Test
    fun aVeryLateLoadStillStartsAtTheRightPosition() = runTest {
        loader.result = { playable() }
        advanceTimeBy(60_000)
        val viewModel = roomViewModel(match, clock())

        match.start.complete(15_000)
        runCurrent()

        assertTrue(player.isPlaying)
        assertEquals(listOf(45_000L), player.seeks)
        assertFalse(viewModel.ready().isPaused)
    }

    @Test
    fun anOnTimeStartDoesNotSeek() = runTest {
        loader.result = { playable() }
        roomViewModel(match, clock())

        match.start.complete(3_000)
        advanceTimeBy(3_001)

        assertTrue(player.isPlaying)
        assertTrue(player.seeks.isEmpty())
    }

    @Test
    fun roomMatchReportsLoadedOnce() = runTest {
        loader.result = { playable() }
        roomViewModel(match, clock())

        assertEquals(1, match.loadedCalls)
        assertEquals("raiz/a/song.ogg", match.preparedWhenLoaded) // a música já carregou
        assertEquals(false, match.playingWhenLoaded) // e ainda não toca

        match.start.complete(100)
        advanceTimeBy(1_000)
        assertEquals(1, match.loadedCalls)
    }

    @Test
    fun roomMatchNeverPauses() = runTest {
        loader.result = { playable() }
        val viewModel = roomViewModel(match, clock())
        viewModel.pause() // segundo plano enquanto espera: não começa pausada
        match.start.complete(100)
        advanceTimeBy(101)
        assertTrue(player.isPlaying)

        viewModel.pause() // Pausar, Voltar, Esc/Start, perda de foco, segundo plano
        interruptions.interrupt() // ligação, outro app tocando, fone desconectado
        interruptions.deny() // o Android negou o foco de áudio

        assertTrue(player.isPlaying)
        assertFalse(viewModel.ready().isPaused)
        assertFalse(viewModel.ready().audioBusy)
        assertEquals(1, interruptions.starts) // ainda pede o foco de áudio (os outros apps se calam)
    }

    @Test
    fun leavingTheRoomMatchMarksLeft() = runTest {
        loader.result = { playable() }
        val viewModel = roomViewModel(match, clock())
        match.start.complete(100)
        advanceTimeBy(101)

        viewModel.leave()
        viewModel.leave() // Sair e depois a tela fechando: uma vez só

        assertEquals(1, match.leftCalls)
        assertEquals(0, match.finishedCalls)
    }

    @Test
    fun leavingWhileWaitingForTheOthersMarksLeft() = runTest {
        loader.result = { playable() }
        val viewModel = roomViewModel(match, clock())

        viewModel.leave()

        assertEquals(1, match.leftCalls)
    }

    @Test
    fun finishingTheRoomMatchTellsTheRoomAndDoesNotMarkLeft() = runTest {
        loader.result = { playable() }
        val viewModel = roomViewModel(match, clock())
        match.start.complete(100)
        advanceTimeBy(101)

        viewModel.onSessionFinished()
        viewModel.leave() // a tela fecha depois do Resultado

        assertEquals(GameplayUiState.Finished(1), viewModel.uiState.value)
        assertEquals(1, match.finishedCalls)
        assertEquals(0, match.leftCalls)
    }

    @Test
    fun aRoomMatchThatCannotLoadLeavesTheRound() = runTest {
        loader.result = { playable(audio = null) }

        val viewModel = roomViewModel(match, clock())

        assertEquals(GameplayUiState.Unavailable(UnavailableReason.NO_AUDIO), viewModel.uiState.value)
        assertEquals(0, match.loadedCalls)
        assertEquals(1, match.leftCalls) // os outros não esperam por mim
    }

    /**
     * O player aquecido informa 120 ms do play até o alto-falante: a sessão começa 120 ms antes do instante
     * combinado, para o som sair nele (Decisão 5b). Aquecido ao carregar, bem antes dele.
     */
    @Test
    fun outputLatencyStartsTheSongEarlier() = runTest {
        player.latencyWhenWarm = 120
        loader.result = { playable() }
        val viewModel = roomViewModel(match, clock())

        match.start.complete(5_000)
        runCurrent()

        advanceTimeBy(4_879)
        assertFalse(player.isPlaying)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(player.isPlaying) // 120 ms antes do instante
        assertEquals(4_880L, testScheduler.currentTime)
        assertTrue(player.seeks.isEmpty()) // do começo
        assertEquals(5_000L, viewModel.ready().countdownEndsAt) // a contagem continua até o instante
    }

    /**
     * Aquece logo depois de carregar, sem esperar o começo: as medidas do relógio no `awaitStart` comem a janela
     * antes do instante, e o arranque do som (~650 ms no PC) fica fora dela. O silêncio não se ouve.
     */
    @Test
    fun warmsUpRightAfterLoading() = runTest {
        player.latencyWhenWarm = 120
        loader.result = { playable() }
        val viewModel = roomViewModel(match, clock())

        assertEquals(0, match.warmUpsWhenLoaded) // o carregou primeiro (os outros esperam por ele)
        assertEquals(1, player.warmUps) // e já aquecido, ainda esperando o começo
        assertTrue(viewModel.ready().waitingForStart)

        match.start.complete(5_000)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, player.warmUps) // uma vez só
        assertTrue(player.isPlaying)
    }

    /** Sem a latência informada (o MediaPlayer, ou a saída ainda arrancando): no instante, com a calibração só. */
    @Test
    fun noLatencyInfoKeepsTheCalibrationOnly() = runTest {
        player.latencyWhenWarm = null
        loader.result = { playable() }
        val viewModel = roomViewModel(match, clock())

        match.start.complete(5_000)
        advanceTimeBy(4_999)
        assertFalse(player.isPlaying)
        advanceTimeBy(1)
        runCurrent()

        assertTrue(player.isPlaying)
        assertEquals(5_000L, testScheduler.currentTime)
        assertEquals(-40L, viewModel.ready().session.tick()) // a posição 0 com o ajuste de áudio (-40 ms)
    }

    /** Uma latência absurda (leitura errada da saída) não adianta a música além do limite. */
    @Test
    fun anAbsurdLatencyIsCapped() = runTest {
        player.latencyWhenWarm = 5_000
        loader.result = { playable() }
        roomViewModel(match, clock())

        match.start.complete(5_000)
        advanceTimeBy(5_000 - GameplayViewModel.MAX_OUTPUT_LATENCY_MS - 1)
        assertFalse(player.isPlaying)
        advanceTimeBy(1)
        runCurrent()

        assertTrue(player.isPlaying)
    }

    /**
     * O começo já passou: começa já, na posição dos outros. O aquecimento (feito ao carregar) não vale: o
     * reposicionamento abre outra saída, fria, e a latência dele não é usada.
     */
    @Test
    fun aLateStartSeeksPastTheWarmUp() = runTest {
        player.latencyWhenWarm = 120
        loader.result = { playable() }
        advanceTimeBy(10_000)
        roomViewModel(match, clock())

        match.start.complete(5_000)
        runCurrent()

        assertEquals(1, player.warmUps) // aqueceu ao carregar, e só
        assertEquals(listOf(5_000L), player.seeks)
        assertEquals(10_000L, testScheduler.currentTime) // sem adiantar pela latência
    }

    @Test
    fun soloMatchIsUnchanged() = runTest {
        loader.result = { playable() }
        val viewModel = viewModel()

        val ready = viewModel.ready()
        assertFalse(ready.inRoom)
        assertFalse(ready.waitingForStart)
        assertNull(ready.countdownEndsAt)
        assertTrue(player.isPlaying) // começa na hora
        assertTrue(player.seeks.isEmpty()) // do começo
        assertTrue(matchStartsAsked.isEmpty())
        assertEquals(0, player.warmUps) // sozinho, nada de aquecer

        viewModel.pause()
        assertTrue(viewModel.ready().isPaused)
        viewModel.resume()
        viewModel.leave() // sozinho, Sair só fecha a tela
        assertFalse(viewModel.ready().isPaused)
        assertTrue(player.isPlaying)
    }
}
