package com.rodrigomachado.riff.feature.room

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
internal actual fun RoomBackHandler(onBack: () -> Unit) = BackHandler(onBack = onBack)

internal actual fun roomLog(message: String, error: Throwable) {
    // Fora do Android (nos testes da JVM), o Log lança: o log nunca derruba nada.
    runCatching { Log.w("RiffRoom", message, error) }
}
