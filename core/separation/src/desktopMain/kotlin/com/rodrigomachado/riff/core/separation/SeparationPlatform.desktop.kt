package com.rodrigomachado.riff.core.separation

internal actual fun separationLog(tag: String, message: String, error: Throwable?) {
    System.err.println("$tag: $message" + (error?.let { " ($it)" } ?: ""))
}
