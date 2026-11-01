package com.rodrigomachado.riff.feature.gameplay

import android.os.SystemClock
import android.provider.Settings.Global
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

actual fun touchUptimeMs(): Long = SystemClock.uptimeMillis()

/** O `eventTime` da tecla é do mesmo relógio do toque (`uptimeMillis`). */
actual fun keyLateByMs(event: KeyEvent): Long = touchUptimeMs() - event.nativeKeyEvent.eventTime

/** "Remover animações" do Android: escala de animação zerada. */
@Composable
actual fun systemAnimationsReduced(): Boolean {
    val context = LocalContext.current
    return remember { Global.getFloat(context.contentResolver, Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

@Composable
actual fun GameBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled = enabled, onBack = onBack)

@Composable
actual fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Sair do app já pausa pelo ON_PAUSE; a cortina de notificações (que só tira o foco) não pausa, como antes. */
@Composable
actual fun OnWindowFocusLost(onLost: () -> Unit) = Unit
