package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.input.key.Key

/** As teclas de fábrica da partida no PC (padrão do Clone Hero; a pausa também no Esc, como no resto do app). */
object DesktopKeys {
    val FRETS = listOf(Key.A, Key.S, Key.J, Key.K, Key.L)
    val STRUM = setOf(Key.DirectionUp, Key.DirectionDown)
    val PAUSE = setOf(Key.Enter, Key.Escape)
}

/**
 * Traduz as teclas da partida no PC em cores, palhetadas e pausa. A repetição automática do teclado (tecla
 * segurada) não vira apertos nem palhetadas repetidas: só conta a primeira descida de cada tecla.
 */
class KeyboardInput(
    private val onFretDown: (lane: Int) -> Unit,
    private val onFretUp: (lane: Int) -> Unit,
    private val onStrum: () -> Unit,
    private val onPause: () -> Unit,
) {
    private val down = mutableSetOf<Key>()

    /** Tecla descendo; verdadeiro se é uma tecla da partida (consumida). */
    fun keyDown(key: Key): Boolean {
        if (!isGameKey(key)) return false
        if (!down.add(key)) return true // repetição automática
        val lane = DesktopKeys.FRETS.indexOf(key)
        when {
            lane >= 0 -> onFretDown(lane)
            key in DesktopKeys.STRUM -> onStrum()
            else -> onPause()
        }
        return true
    }

    /** Tecla subindo; verdadeiro se é uma tecla da partida. */
    fun keyUp(key: Key): Boolean {
        if (!isGameKey(key)) return false
        down.remove(key)
        val lane = DesktopKeys.FRETS.indexOf(key)
        if (lane >= 0) onFretUp(lane)
        return true
    }

    /** Esquece as teclas seguradas (a janela perdeu o foco: as subidas não vão chegar). */
    fun forgetHeldKeys() = down.clear()

    private fun isGameKey(key: Key) = key in DesktopKeys.FRETS || key in DesktopKeys.STRUM || key in DesktopKeys.PAUSE
}
