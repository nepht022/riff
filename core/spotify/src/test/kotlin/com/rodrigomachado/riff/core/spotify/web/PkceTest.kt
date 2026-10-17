package com.rodrigomachado.riff.core.spotify.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceTest {
    /** SHA-256 em base64url sem "=" (valor conferido à parte com o hashlib do Python). */
    @Test
    fun theChallengeIsTheBase64UrlOfTheSha256() {
        assertEquals(
            "SknJ0xIQubOXaYoR0N2NzkJ3Xf_82na85magttN4yEE",
            Pkce.challenge("dBjftJeZ4CVP-mJ92K1N4bPpN8OJMRF2dX0cgUpQjnc"),
        )
    }

    @Test
    fun theVerifierIsUrlSafeAndLongEnough() {
        val verifier = Pkce.verifier()

        assertTrue(verifier.length in 43..128)
        assertTrue(verifier.all { it.isLetterOrDigit() || it in "-._~" })
    }
}
