package com.rodrigomachado.riff.core.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalWindowInfo

/** Um ajuste do relógio do sistema no meio da música não vira uma tecla apertada segundos atrás. */
private const val MAX_KEY_LATE_MS = 200L

/** O evento do AWT traz o instante da tecla no relógio de parede (`currentTimeMillis`); sem ele, agora. */
actual fun KeyEvent.lateByMs(): Long {
    val awt = awtEventOrNull ?: return 0
    return (System.currentTimeMillis() - awt.`when`).coerceIn(0, MAX_KEY_LATE_MS)
}

@Composable
actual fun OnWindowFocusLost(onLost: () -> Unit) {
    val focused = LocalWindowInfo.current.isWindowFocused
    val lost by rememberUpdatedState(onLost)
    LaunchedEffect(focused) { if (!focused) lost() }
}
