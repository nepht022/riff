package com.rodrigomachado.riff.core.spotify.web

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Prova de posse do código (RFC 7636, S256): a autorização sem segredo guardado no app. */
object Pkce {
    private const val VERIFIER_BYTES = 64
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun verifier(random: SecureRandom = SecureRandom()): String =
        encoder.encodeToString(ByteArray(VERIFIER_BYTES).also(random::nextBytes))

    fun challenge(verifier: String): String =
        encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}
