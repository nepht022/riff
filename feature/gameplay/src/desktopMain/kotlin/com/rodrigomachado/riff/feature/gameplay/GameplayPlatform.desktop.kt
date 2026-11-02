package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.runtime.Composable

private const val NANOS_PER_MS = 1_000_000

actual val playsByTouch = false

actual fun touchUptimeMs(): Long = System.nanoTime() / NANOS_PER_MS

@Composable
actual fun systemAnimationsReduced(): Boolean = false

@Composable
actual fun GameBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun KeepScreenOn() = Unit
