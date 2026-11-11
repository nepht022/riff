package com.rodrigomachado.riff

import com.rodrigomachado.riff.feature.gameplay.GameplayRoute
import com.rodrigomachado.riff.feature.results.ResultsRoute
import com.rodrigomachado.riff.feature.room.RoomResultsRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O Resultado sabe se a partida foi de sala (só "Voltar à sala", sem "Jogar de novo") e de que sala e rodada, para o
 * "Ver a sala" abrir o Resultado da sala.
 */
class RoomResultsRouteTest {
    @Test
    fun roomResultHasNoPlayAgain() {
        val results = resultsOf(GameplayRoute("a", "EXPERT", "BCDF", round = 1), 7)

        assertEquals(ResultsRoute(7, roomCode = "BCDF", round = 1), results)
        assertTrue(results.inRoom)
    }

    @Test
    fun seeTheRoomOpensTheRoomResultsOfTheRound() {
        val results = resultsOf(GameplayRoute("a", "EXPERT", "BCDF", round = 3), 7)

        assertEquals(RoomResultsRoute("BCDF", 3), roomResultsOf(results))
    }

    @Test
    fun soloResultIsUnchanged() {
        val results = resultsOf(GameplayRoute("a", "EXPERT"), 7)

        assertEquals(ResultsRoute(7), results)
        assertFalse(results.inRoom)
        assertNull(roomResultsOf(results))
    }
}
