package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.model.SpotifyError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {
    @Test
    fun onlyALostConnectionIsRetriedAutomatically() {
        assertTrue(ReconnectPolicy.shouldRetry(SpotifyError.DISCONNECTED))
        SpotifyError.entries.filter { it != SpotifyError.DISCONNECTED }.forEach {
            assertFalse("$it precisa do usuário", ReconnectPolicy.shouldRetry(it))
        }
    }

    @Test
    fun waitDoublesUpToThirtySeconds() {
        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L),
            (0..6).map(ReconnectPolicy::delayMs),
        )
    }

    @Test
    fun configWithoutClientIdIsNotConfigured() {
        assertFalse(SpotifyConfig(clientId = " ", redirectUri = "riff://callback").isConfigured)
        assertTrue(SpotifyConfig(clientId = "abc", redirectUri = "riff://callback").isConfigured)
    }
}
