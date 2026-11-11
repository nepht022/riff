package com.rodrigomachado.riff.feature.gameplay

import com.rodrigomachado.riff.core.game.TimeSource
import com.rodrigomachado.riff.core.model.Judgment
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

/** O placar ao vivo na partida de sala (Decisão 2 da M3): publica o meu a cada 1 s e no fim, e recebe o dos outros. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameplayLiveBoardTest : GameplayViewModelTestBase() {
    private val board = FakeLiveBoard()

    /** O começo da sala de mentira: o teste decide o instante do começo. */
    private class FakeMatchStart : MatchStart {
        val start = CompletableDeferred<Long>()

        override suspend fun loaded() = Unit

        override suspend fun awaitStart(): Long = start.await()

        override suspend fun left() = Unit

        override suspend fun finished() = Unit
    }

    private val match = FakeMatchStart()

    /** O relógio da partida é o tempo virtual do teste: o `delay` e o `nowMs` andam juntos. */
    private fun TestScope.clock() = TimeSource { testScheduler.currentTime }

    /** Os quadros da partida por [ms] de tempo virtual, um a cada [FRAME_MS] (o que o `FrameLoop` da tela faz). */
    private fun TestScope.frames(viewModel: GameplayViewModel, ms: Long) {
        repeat((ms / FRAME_MS).toInt()) {
            advanceTimeBy(FRAME_MS)
            runCurrent()
            viewModel.onFrame()
        }
    }

    @Test
    fun publishesEverySecondAndOnFinish() = runTest {
        loader.result = { playable() }
        val instants = mutableListOf<Long>()
        board.onPublish = { instants += testScheduler.currentTime }
        roomViewModel(match, clock(), liveBoard = board).also { viewModel ->
            assertEquals(listOf("ABCD" to 3), liveBoardsAsked)

            // Esperando o começo e contando (com quadros: a contagem aparece na pista): nada ainda.
            match.start.complete(2_000)
            frames(viewModel, 1_900)
            assertTrue(board.published.isEmpty())

            // Do começo em diante, a cada 1 s (o primeiro já no começo: os outros me veem desde o 0).
            frames(viewModel, 3_100)
            assertEquals(listOf(2_000L, 3_000L, 4_000L, 5_000L), instants)
            assertTrue(board.published.none { it.finished })

            // No fim, o final (com as notas todas julgadas) e mais nada depois.
            frames(viewModel, 500)
            viewModel.onSessionFinished()
            runCurrent()
            val final = board.published.last()
            assertTrue(final.finished)
            assertEquals(2, final.notes)
            assertEquals(0, final.hits)
            val count = board.published.size
            frames(viewModel, 10_000)
            assertEquals(count, board.published.size)
        }
    }

    @Test
    fun theScorePublishedIsTheEngines() = runTest {
        loader.result = { playable() }
        val viewModel = roomViewModel(match, clock(), liveBoard = board)
        match.start.complete(0)
        runCurrent()
        val engine = (viewModel.uiState.value as GameplayUiState.Ready).session.engine

        // Uma nota perdida (a primeira, aos 1 s): a próxima publicação a conta.
        engine.update(1_500)
        frames(viewModel, 1_000)

        val last = board.published.last()
        assertEquals(engine.score, last.score)
        assertEquals(1, last.notes)
        assertEquals(0, last.hits)
        assertEquals(engine.accuracy(), last.accuracy)
        assertFalse(last.finished)
    }

    /**
     * No meio da partida, a precisão é sobre as notas que já passaram (o BOM vale meia, como no motor), não sobre as
     * notas do mapa inteiro; no fim, a do motor.
     */
    @Test
    fun partialAccuracyIsOverThePassedNotes() = runTest {
        loader.result = { playable() }
        val viewModel = roomViewModel(match, clock(), liveBoard = board)
        match.start.complete(0)
        runCurrent()
        val engine = (viewModel.uiState.value as GameplayUiState.Ready).session.engine

        assertEquals(Judgment.GOOD, engine.onPress(0, 1_080)) // a 1.ª nota, 80 ms atrasada: BOM
        frames(viewModel, 1_000)
        assertEquals(0.5f, board.published.last().accuracy, 0.0001f) // 1 BOM em 1 nota (não 0,25: em 2)

        engine.update(2_500) // a 2.ª passou sem tocar
        frames(viewModel, 1_000)
        assertEquals(0.25f, board.published.last().accuracy, 0.0001f)

        viewModel.onSessionFinished()
        runCurrent()
        assertEquals(engine.accuracy(), board.published.last().accuracy)
    }

    @Test
    fun othersReachTheMatch() = runTest {
        loader.result = { playable() }
        val viewModel = roomViewModel(match, clock(), liveBoard = board)
        val ana = LiveScore("ana", "Ana", 1_200, 4, 9, 0.5f, finished = false)

        board.others.value = listOf(ana)
        runCurrent()

        assertEquals(listOf(ana), viewModel.liveOthers!!.value)
    }

    /** Partida sozinho: nenhum placar ao vivo pedido, nenhum bloco e nenhuma escrita. */
    @Test
    fun soloMatchHasNoLiveBoard() = runTest {
        loader.result = { playable() }
        val viewModel = viewModel()
        repeat(50) { viewModel.onFrame() }
        viewModel.onSessionFinished()
        runCurrent()

        assertTrue(liveBoardsAsked.isEmpty())
        assertNull(viewModel.liveOthers)
    }

    private companion object {
        const val FRAME_MS = 100L
    }
}
