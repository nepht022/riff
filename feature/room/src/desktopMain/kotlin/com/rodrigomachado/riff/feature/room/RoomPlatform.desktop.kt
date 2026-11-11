package com.rodrigomachado.riff.feature.room

import androidx.compose.runtime.Composable

@Composable
internal actual fun RoomBackHandler(onBack: () -> Unit) = Unit

internal actual fun roomLog(message: String, error: Throwable) {
    System.err.println("RiffRoom: $message: $error")
}
