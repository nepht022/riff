package com.rodrigomachado.riff

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import org.junit.Assert.assertEquals
import org.junit.Test

/** O gesto de voltar do celular troca a tela igual ao botão Voltar do app, sem a miniatura encolhendo. */
class RiffNavTransitionsTest {
    @Test
    fun theBackGestureFadesLikeTheBackButton() {
        assertEquals(fadeIn(tween(700)), RiffNavTransitions.backEnter)
        assertEquals(fadeOut(tween(700)), RiffNavTransitions.backExit)
    }
}
