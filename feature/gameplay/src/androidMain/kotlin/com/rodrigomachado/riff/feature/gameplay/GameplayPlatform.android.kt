package com.rodrigomachado.riff.feature.gameplay

import android.os.SystemClock
import android.provider.Settings.Global
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

actual val playsByTouch = true

actual fun touchUptimeMs(): Long = SystemClock.uptimeMillis()

actual fun matchSyncLog(message: String) {
    // O log de depuração nunca derruba a partida (fora do Android, nos testes da JVM, o Log lança).
    runCatching { Log.i("RiffSync", message) }
}

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
