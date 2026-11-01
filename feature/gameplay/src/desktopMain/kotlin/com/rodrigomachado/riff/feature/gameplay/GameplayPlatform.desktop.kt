package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalWindowInfo

private const val NANOS_PER_MS = 1_000_000

/** Um ajuste do relógio do sistema no meio da música não vira uma tecla apertada segundos atrás. */
private const val MAX_KEY_LATE_MS = 200L

actual fun touchUptimeMs(): Long = System.nanoTime() / NANOS_PER_MS

/** O evento do AWT traz o instante da tecla no relógio de parede (`currentTimeMillis`); sem ele, agora. */
actual fun keyLateByMs(event: KeyEvent): Long {
    val awt = event.awtEventOrNull ?: return 0
    return (System.currentTimeMillis() - awt.`when`).coerceIn(0, MAX_KEY_LATE_MS)
}

@Composable
actual fun systemAnimationsReduced(): Boolean = false

@Composable
actual fun GameBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun KeepScreenOn() = Unit

@Composable
actual fun OnWindowFocusLost(onLost: () -> Unit) {
    val focused = LocalWindowInfo.current.isWindowFocused
    val lost by rememberUpdatedState(onLost)
    LaunchedEffect(focused) { if (!focused) lost() }
}
