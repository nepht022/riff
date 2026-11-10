package com.rodrigomachado.riff

import com.rodrigomachado.riff.feature.gameplay.GameplayRoute
import com.rodrigomachado.riff.feature.results.ResultsRoute
import org.junit.Assert.assertEquals
import org.junit.Test

/** O Resultado sabe se a partida foi de sala (para mostrar só "Voltar à sala", sem "Jogar de novo"). */
class RoomResultsRouteTest {
    @Test
    fun roomResultHasNoPlayAgain() {
        assertEquals(ResultsRoute(7, inRoom = true), resultsOf(GameplayRoute("a", "EXPERT", "BCDF", round = 1), 7))
    }

    @Test
    fun soloResultIsUnchanged() {
        assertEquals(ResultsRoute(7, inRoom = false), resultsOf(GameplayRoute("a", "EXPERT"), 7))
    }
}
