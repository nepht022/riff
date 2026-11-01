package com.rodrigomachado.riff.core.audio

import android.os.Process
import android.os.SystemClock
import android.util.Log

private const val TAG = "RiffAudio"

internal actual fun audioLog(message: String, error: Throwable?) {
    Log.w(TAG, message, error)
}

internal actual fun uptimeMs(): Long = SystemClock.uptimeMillis()

internal actual fun raiseAudioThreadPriority() = Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
