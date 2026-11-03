package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import com.rodrigomachado.riff.core.model.ControlAction
import com.rodrigomachado.riff.core.model.Controls
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.input.OnWindowFocusLost
import com.rodrigomachado.riff.core.ui.input.lateByMs
import com.rodrigomachado.riff.core.ui.input.menuKeysEnabled

/**
 * As teclas da partida ([keys], as escolhidas) ligadas à [session]: cores e palheta com o atraso de cada evento (como
 * o toque), e a tecla da pausa (a escolhida ou o Esc) em [onPauseKey].
 */
internal class GameKeyboard(private val session: GameSession, keys: Map<ControlAction, Long>, onPauseKey: () -> Unit) {
    private var lateByMs = 0L
    private val input = KeyboardInput(
        keys = keys,
        onFretDown = { session.fretDown(it, lateByMs) },
        onFretUp = { session.fretUp(it, lateByMs) },
        onStrum = { session.strum(lateByMs) },
        onPause = onPauseKey,
    )

    fun onKeyEvent(event: KeyEvent): Boolean {
        lateByMs = event.lateByMs()
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
 * Os botões da pausa pelas setas (só no PC; no celular a pausa fica igual): a primeira seta foca o [first] (o
 * Continuar), as seguintes andam entre os botões da pausa ([Modifier.pauseMenu], de onde o foco não sai). Na pausa as
 * setas não palhetam (a sessão já as ignoraria); as subidas seguem para o teclado da partida, que esquece a tecla.
 */
internal class PauseMenu {
    val first = FocusRequester()
    var hasFocus = false

    /** Uma tecla na pausa; verdadeiro se era uma seta (consumida). */
    fun onKeyEvent(event: KeyEvent, focus: FocusManager): Boolean {
        if (!menuKeysEnabled || event.type != KeyEventType.KeyDown) return false
        val direction = when (event.key) {
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            Key.DirectionLeft -> FocusDirection.Left
            Key.DirectionRight -> FocusDirection.Right
            else -> return false
        }
        if (hasFocus) focus.moveFocus(direction) else first.requestFocus()
        return true
    }
}

/**
 * Os botões da pausa como um grupo do [menu]: a seta não leva o foco para fora (o placar atrás do véu). No celular é
 * `this`.
 */
internal fun Modifier.pauseMenu(menu: PauseMenu): Modifier = if (!menuKeysEnabled) {
    this
} else {
    onFocusChanged { menu.hasFocus = it.hasFocus }
        .focusProperties { onExit = { cancelFocusChange() } }
        .focusGroup()
}

/**
 * O teclado na partida (PC, com as teclas escolhidas em [controls]; no celular com teclado também, com as de fábrica,
 * e o toque segue igual): o palco pede o foco ao aparecer e ao abrir ou fechar a pausa (o foco pode ter ido a um botão
 * dela). A tecla da pausa (Enter de fábrica) e o Esc pausam; na pausa, continuam, como o Continuar (com o mesmo som).
 * Na pausa do PC as setas andam pelos botões dela ([pauseMenu]); um botão com o foco recebe o Enter antes, e vale o
 * botão. A janela perdendo o foco pausa e solta as cores seguradas.
 */
@Composable
internal fun rememberGameKeys(
    session: GameSession,
    controls: Controls,
    isPaused: Boolean,
    pauseMenu: PauseMenu,
    onPause: () -> Unit,
    onResume: () -> Unit,
): Modifier {
    val paused by rememberUpdatedState(isPaused)
    val pause by rememberUpdatedState(onPause)
    val resume by rememberUpdatedState(withMenuSound(MenuSound.CONFIRM, onResume))
    val keys = gameKeys(controls)
    val keyboard = remember(session, keys) { GameKeyboard(session, keys) { if (paused) resume() else pause() } }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(isPaused) { focus.requestFocus() }
    OnWindowFocusLost {
        pause() // antes de soltar: soltar uma cor tocando poderia acertar um pull-off
        keyboard.letGo()
    }
    return Modifier
        .onKeyEvent { (paused && pauseMenu.onKeyEvent(it, focusManager)) || keyboard.onKeyEvent(it) }
        .focusRequester(focus)
        .focusTarget()
}
