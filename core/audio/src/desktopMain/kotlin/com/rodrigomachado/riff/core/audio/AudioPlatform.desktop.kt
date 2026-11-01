package com.rodrigomachado.riff.core.audio

internal actual fun audioLog(message: String, error: Throwable?) {
    System.err.println("RiffAudio: $message" + (error?.let { " ($it)" } ?: ""))
}

internal actual fun uptimeMs(): Long = System.nanoTime() / NANOS_PER_MS

internal actual fun raiseAudioThreadPriority() {
    Thread.currentThread().priority = Thread.MAX_PRIORITY
}

private const val NANOS_PER_MS = 1_000_000L
