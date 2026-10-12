package com.rodrigomachado.riff

import org.junit.Assert.assertEquals
import org.junit.Test

class BackNavigationTest {
    private val back = BackNavigation()

    private fun show(vararg ids: String) = ids.map { back.onShown(it) }

    @Test
    fun openingScreensIsNotGoingBackButReturningToOneIs() {
        // Tocando agora → Biblioteca → Partida, e voltar até a Biblioteca.
        assertEquals(listOf(false, false, false, true), show("inicio", "biblioteca", "partida", "biblioteca"))
    }

    @Test
    fun theSameScreenShownAgainIsNotGoingBack() {
        // Girar a tela mostra de novo a tela atual.
        assertEquals(listOf(false, false, false), show("inicio", "biblioteca", "biblioteca"))
    }

    @Test
    fun aReturnMarkedSilentIsNotGoingBackButOnlyOnce() {
        show("inicio", "biblioteca")
        back.silenceNextReturn() // escolheu o mapa da música: volta sozinho, como confirmação

        assertEquals(false, back.onShown("inicio"))
        assertEquals(listOf(false, true), show("configuracoes", "inicio"))
    }

    @Test
    fun replacingTheTopScreenThenGoingBackStillCounts() {
        // Resultado → Jogar de novo troca o resultado por uma partida nova; voltar dela cai na biblioteca.
        assertEquals(
            listOf(false, false, false, false, true),
            show("inicio", "biblioteca", "resultado", "partida2", "biblioteca"),
        )
    }
}
