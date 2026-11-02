package com.rodrigomachado.riff.feature.calibration

private const val NANOS_PER_MS = 1_000_000

actual fun calibrationClockMs(): Long = System.nanoTime() / NANOS_PER_MS

actual val tapsByTouch = false
