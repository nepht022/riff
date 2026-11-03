package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.input.Gamepad
import com.rodrigomachado.riff.core.ui.input.LocalGamepad
import com.rodrigomachado.riff.core.ui.input.LocalPadMenuSuspension
import com.rodrigomachado.riff.core.ui.input.PadMenuSuspension
import com.rodrigomachado.riff.core.ui.theme.RiffTheme

/** A janela do teste com o foco nas mãos do teste (o Alt+Tab de mentira). */
class ControlledWindowInfo(real: WindowInfo) : WindowInfo by real {
    override var isWindowFocused by mutableStateOf(true)
}

fun readyState(session: GameSession, isPaused: Boolean = false) =
    GameplayUiState.Ready("Música", "Banda", Difficulty.EXPERT, session, Settings.DEFAULT, isPaused = isPaused)

/** Uma partida tocando [notes], com o relógio parado em [positionMs]. */
fun playingSession(vararg notes: Note, positionMs: Long = 0): GameSession {
    val player = FakeSongPlayer().apply { this.positionMs = positionMs }
    return GameSession(notes.toList(), player, FakeSfxPlayer(), FakeTime(), audioOffsetMs = 0).also {
        it.start()
        it.tick()
    }
}

/**
 * Mostra o palco da partida (sem o loop de quadros) numa janela com o foco controlado, que é devolvida; com o
 * controle [gamepad] (nulo: sem controle, como no celular) e a suspensão dele nos menus ([suspension], a do app do PC).
 */
fun ComposeContentTestRule.showStage(
    state: () -> GameplayUiState.Ready,
    onPause: () -> Unit = {},
    gamepad: Gamepad? = null,
    suspension: PadMenuSuspension? = null,
    onExit: () -> Unit = {},
    onResume: () -> Unit = {},
): ControlledWindowInfo {
    lateinit var window: ControlledWindowInfo
    setContent {
        val real = LocalWindowInfo.current
        window = remember { ControlledWindowInfo(real) }
        CompositionLocalProvider(
            LocalWindowInfo provides window,
            LocalGamepad provides gamepad,
            LocalPadMenuSuspension provides suspension,
        ) {
            Stage(state(), onPause, onResume, onExit)
        }
    }
    waitForIdle()
    return window
}

@Composable
private fun Stage(state: GameplayUiState.Ready, onPause: () -> Unit, onResume: () -> Unit, onExit: () -> Unit) {
    RiffTheme {
        GameStage(
            state,
            reducedMotion = true,
            onPause = onPause,
            onResume = onResume,
            onExit = onExit,
            onSessionFinished = {},
            animate = false,
        )
    }
}
