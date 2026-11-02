package com.rodrigomachado.riff.core.ui.input

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyEvent

/** O `eventTime` da tecla é do mesmo relógio do toque (`uptimeMillis`). */
actual fun KeyEvent.lateByMs(): Long = SystemClock.uptimeMillis() - nativeKeyEvent.eventTime

/** Sair do app já passa pelo ciclo de vida; a cortina de notificações (que só tira o foco) não conta, como antes. */
@Composable
actual fun OnWindowFocusLost(onLost: () -> Unit) = Unit
