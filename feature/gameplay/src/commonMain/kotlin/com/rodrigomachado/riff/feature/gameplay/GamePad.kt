package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalWindowInfo
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.input.Gamepad
import com.rodrigomachado.riff.core.ui.input.LocalGamepad
import com.rodrigomachado.riff.core.ui.input.PadAction
import com.rodrigomachado.riff.core.ui.input.PadEvent
import com.rodrigomachado.riff.core.ui.input.TakePadFromMenus
import com.rodrigomachado.riff.core.ui.input.nowNanos
import kotlinx.coroutines.launch

/** Um evento que esperou mais que isso (a fila travou) não vira uma nota tocada segundos atrás; como o teclado. */
private const val MAX_PAD_LATE_MS = 200L
private const val NANOS_PER_MS = 1_000_000L

/**
 * O controle na partida (PC; no celular [LocalGamepad] é nulo e nada muda): as cores e a palheta chegam à mesma
 * [session] do teclado, descontando quanto cada evento esperou; Start pausa e, na pausa, continua (com o som do
 * Continuar, como o Enter). Com a janela sem foco (Alt+Tab) o controle é ignorado: o GLFW o lê em segundo plano.
 * O controle desconectado no meio da música pausa (as cores chegam soltas logo depois). Tocando, nenhum botão vira
 * tecla nos menus ([TakePadFromMenus]: o ✕ que acerta a nota não confirma nada); na pausa, só o da pausa
 * ([pauseButton]) fica com a partida (Start continua, sem virar também um Enter), e os outros andam nos botões dela.
 */
@Composable
internal fun HandleGamepad(
    session: GameSession,
    isPaused: Boolean,
    pauseButton: String?,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val gamepad = LocalGamepad.current ?: return
    val window = LocalWindowInfo.current
    val paused by rememberUpdatedState(isPaused)
    TakePadFromMenus { !paused || it == pauseButton }
    val pause by rememberUpdatedState(onPause)
    val resume by rememberUpdatedState(withMenuSound(MenuSound.CONFIRM, onResume))
    // Na thread da janela, como o teclado: a sessão não é thread-safe.
    LaunchedEffect(gamepad, session) {
        val unplug = UnplugWatch(gamepad) { if (!paused) pause() }
        launch { gamepad.connected.collect { unplug.check() } }
        gamepad.events.collect { event ->
            // O cabo saiu: pausa antes de as cores soltas chegarem (soltar uma cor tocando poderia acertar um
            // pull-off), mesmo que este evento chegue antes da mudança do `connected`.
            unplug.check()
            // O foco de agora, não o da composição: o evento pode chegar antes de a perda do foco recompor.
            if (window.isWindowFocused) {
                onEvent(session, event, if (paused) resume else pause)
            }
        }
    }
}

/**
 * Avisa [onUnplugged] uma vez por desconexão: só na passagem de ligado a desligado (começar sem controle não pausa).
 */
private class UnplugWatch(private val gamepad: Gamepad, private val onUnplugged: () -> Unit) {
    private var wasConnected = gamepad.connected.value

    fun check() {
        val connected = gamepad.connected.value
        if (wasConnected && !connected) onUnplugged()
        wasConnected = connected
    }
}

private fun onEvent(session: GameSession, event: PadEvent, onStart: () -> Unit) {
    val lateByMs = ((nowNanos() - event.atNanos) / NANOS_PER_MS).coerceIn(0, MAX_PAD_LATE_MS)
    val lane = event.action.fretLane
    when {
        lane != null && event.down -> session.fretDown(lane, lateByMs)
        lane != null -> session.fretUp(lane, lateByMs)
        !event.down -> Unit
        event.action == PadAction.STRUM -> session.strum(lateByMs)
        event.action == PadAction.PAUSE -> onStart()
    }
}
