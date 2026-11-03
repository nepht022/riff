package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.input.key.Key
import com.rodrigomachado.riff.core.model.ControlAction
import com.rodrigomachado.riff.core.model.Controls

/**
 * As teclas de fábrica da partida (padrão do Clone Hero) pelas constantes do Compose, com o código de cada plataforma:
 * no PC são iguais a `Controls.DEFAULT_KEYS`; no celular (teclado físico), os códigos do Android.
 */
internal val FACTORY_KEYS: Map<ControlAction, Long> = mapOf(
    ControlAction.FRET_1 to Key.A,
    ControlAction.FRET_2 to Key.S,
    ControlAction.FRET_3 to Key.J,
    ControlAction.FRET_4 to Key.K,
    ControlAction.FRET_5 to Key.L,
    ControlAction.STRUM_UP to Key.DirectionUp,
    ControlAction.STRUM_DOWN to Key.DirectionDown,
    ControlAction.PAUSE to Key.Enter,
).mapValues { it.value.keyCode }

/**
 * As teclas da partida: as escolhidas no PC. No celular, as de fábrica: os códigos gravados são os do PC, e lá não há
 * escolha (a aba Controles é só do PC).
 */
internal fun gameKeys(controls: Controls): Map<ControlAction, Long> = if (playsByTouch) FACTORY_KEYS else controls.keys

/**
 * Traduz as teclas da partida em cores, palhetadas e pausa, pelas teclas escolhidas [keys] (o `Key.keyCode` de cada
 * ação); o Esc pausa sempre, além da escolhida. A repetição automática do teclado (tecla segurada) não vira apertos
 * nem palhetadas repetidas: só conta a primeira descida de cada tecla.
 */
class KeyboardInput(
    private val keys: Map<ControlAction, Long> = FACTORY_KEYS,
    private val onFretDown: (lane: Int) -> Unit,
    private val onFretUp: (lane: Int) -> Unit,
    private val onStrum: () -> Unit,
    private val onPause: () -> Unit,
) {
    private val down = mutableSetOf<Key>()

    /** Tecla descendo; verdadeiro se é uma tecla da partida (consumida). */
    fun keyDown(key: Key): Boolean {
        val action = actionOf(key) ?: return false
        if (!down.add(key)) return true // repetição automática
        when (action) {
            ControlAction.STRUM_UP, ControlAction.STRUM_DOWN -> onStrum()
            ControlAction.PAUSE -> onPause()
            else -> onFretDown(FRETS.indexOf(action))
        }
        return true
    }

    /** Tecla subindo; verdadeiro se é uma tecla da partida. */
    fun keyUp(key: Key): Boolean {
        val action = actionOf(key) ?: return false
        down.remove(key)
        val lane = FRETS.indexOf(action)
        if (lane >= 0) onFretUp(lane)
        return true
    }

    /** Esquece as teclas seguradas (a janela perdeu o foco: as subidas não vão chegar). */
    fun forgetHeldKeys() = down.clear()

    private fun actionOf(key: Key): ControlAction? =
        if (key == Key.Escape) ControlAction.PAUSE else keys.entries.firstOrNull { it.value == key.keyCode }?.key

    private companion object {
        val FRETS = listOf(
            ControlAction.FRET_1,
            ControlAction.FRET_2,
            ControlAction.FRET_3,
            ControlAction.FRET_4,
            ControlAction.FRET_5,
        )
    }
}
