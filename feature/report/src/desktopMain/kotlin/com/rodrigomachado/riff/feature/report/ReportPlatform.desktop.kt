package com.rodrigomachado.riff.feature.report

internal actual fun reportLog(message: String) {
    System.err.println("RiffReport: $message")
}
