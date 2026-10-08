package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.model.SpotifyError

/** Reconexão com backoff (spec §7). Erros de configuração, login ou autorização dependem do usuário. */
object ReconnectPolicy {
    private val DELAYS_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000)

    fun shouldRetry(error: SpotifyError): Boolean = error == SpotifyError.DISCONNECTED

    fun delayMs(attempt: Int): Long = DELAYS_MS[attempt.coerceIn(0, DELAYS_MS.lastIndex)]
}
