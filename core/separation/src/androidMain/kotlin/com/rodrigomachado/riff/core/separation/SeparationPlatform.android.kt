package com.rodrigomachado.riff.core.separation

import android.util.Log

internal actual fun separationLog(tag: String, message: String, error: Throwable?) {
    if (error == null) Log.i(tag, message) else Log.w(tag, message, error)
}
