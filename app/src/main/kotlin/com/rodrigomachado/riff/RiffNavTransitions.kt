package com.rodrigomachado.riff

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

/**
 * Transição do gesto de voltar do celular. O padrão da navegação encolhe a tela antiga numa miniatura
 * (voltar preditivo); aqui ela esmaece como no botão Voltar do app (o mesmo `tween` padrão de 700 ms).
 */
object RiffNavTransitions {
    private const val FADE_MS = 700
    val backEnter: EnterTransition = fadeIn(tween(FADE_MS))
    val backExit: ExitTransition = fadeOut(tween(FADE_MS))
}
