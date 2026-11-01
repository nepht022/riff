package com.rodrigomachado.riff.feature.calibration

import android.os.SystemClock

actual fun calibrationClockMs(): Long = SystemClock.uptimeMillis()

actual val tapsByTouch = true
