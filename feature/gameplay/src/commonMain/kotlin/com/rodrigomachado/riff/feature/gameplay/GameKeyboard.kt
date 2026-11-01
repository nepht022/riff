package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.withMenuSound

/**
 * As teclas da partida ligadas à [session]: cores e palheta com o atraso de cada evento (como o toque), e a tecla
 * da pausa em [onPauseKey].
 */
internal class GameKeyboard(private val session: GameSession, onPauseKey: () -> Unit) {
    private var lateByMs = 0L
    private val input = KeyboardInput(
        onFretDown = { session.fretDown(it, lateByMs) },
        onFretUp = { session.fretUp(it, lateByMs) },
        onStrum = { session.strum(lateByMs) },
        onPause = onPauseKey,
    )

    fun onKeyEvent(event: KeyEvent): Boolean {
        lateByMs = keyLateByMs(event)
        return when (event.type) {
            KeyEventType.KeyDown -> input.keyDown(event.key)
            KeyEventType.KeyUp -> input.keyUp(event.key)
            else -> false
        }
    }

    /** As subidas das teclas não vão chegar (a janela perdeu o foco): solta as cores e esquece as teclas. */
    fun letGo() {
        session.releaseAllFrets()
        input.forgetHeldKeys()
    }
}

/**
 * O teclado na partida (PC; no celular com teclado também, e o toque segue igual): o palco pede o foco ao aparecer
 * e ao abrir ou fechar a pausa (o foco pode ter ido a um botão dela). Enter/Esc pausam; na pausa, continuam, como o
 * Continuar (com o mesmo som). Um botão da pausa com o foco (Tab) recebe o Enter antes, e vale o botão.
 * A janela perdendo o foco pausa e solta as cores seguradas.
 */
@Composable
internal fun rememberGameKeys(
    session: GameSession,
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
): Modifier {
    val paused by rememberUpdatedState(isPaused)
    val pause by rememberUpdatedState(onPause)
    val resume by rememberUpdatedState(withMenuSound(MenuSound.CONFIRM, onResume))
    val keyboard = remember(session) { GameKeyboard(session) { if (paused) resume() else pause() } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(isPaused) { focus.requestFocus() }
    OnWindowFocusLost {
        pause() // antes de soltar: soltar uma cor tocando poderia acertar um pull-off
        keyboard.letGo()
    }
    return Modifier.onKeyEvent(keyboard::onKeyEvent).focusRequester(focus).focusTarget()
}
