package com.rodrigomachado.riff.feature.report

import android.util.Log

private const val TAG = "RiffReport"

internal actual fun reportLog(message: String) {
    Log.w(TAG, message)
}
