package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.model.SpotifyError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectorTest {
    @Test
    fun twoReportsOfTheSameDropReconnectOnce() = runTest {
        var reconnects = 0
        val reconnector = Reconnector(backgroundScope) { reconnects++ }

        // A assinatura e o listener da conexão avisam a mesma queda.
        reconnector.lost(SpotifyError.DISCONNECTED)
        reconnector.lost(SpotifyError.DISCONNECTED)
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(1, reconnects)
    }

    @Test
    fun aNewDropAfterTheRetryWaitsTheNextStep() = runTest {
        val at = mutableListOf<Long>()
        val reconnector = Reconnector(backgroundScope) { at += testScheduler.currentTime }

        reconnector.lost(SpotifyError.DISCONNECTED)
        reconnector.lost(SpotifyError.DISCONNECTED)
        advanceTimeBy(1_001)
        reconnector.lost(SpotifyError.DISCONNECTED) // a tentativa falhou
        advanceTimeBy(10_000)

        assertEquals(listOf(1_000L, 3_001L), at)
    }

    @Test
    fun errorsThatDependOnTheUserAreNotRetried() = runTest {
        var reconnects = 0
        val reconnector = Reconnector(backgroundScope) { reconnects++ }

        reconnector.lost(SpotifyError.NOT_AUTHORIZED)
        advanceTimeBy(60_000)

        assertEquals(0, reconnects)
    }

    @Test
    fun aRequestTheSdkNeverAnswersGivesUpAfterTheTimeout() = runTest {
        val result = awaitCallback<String>(timeoutMs = 2_000) { _, _ -> }

        assertNull(result)
        assertEquals(2_000, testScheduler.currentTime)
    }
}
