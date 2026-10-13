package com.rodrigomachado.riff.core.separation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ThermalGateTest {
    @Test
    fun coolPassesStraightThrough() = runTest {
        val pauses = mutableListOf<Boolean>()
        ThermalGate(hot = { false }, cool = { true }).awaitCool { pauses += it }
        assertEquals(emptyList<Boolean>(), pauses)
    }

    @Test
    fun hotPausesUntilItCoolsDown() = runTest {
        var reads = 0
        val pauses = mutableListOf<Boolean>()
        ThermalGate(hot = { true }, cool = { ++reads >= 3 }, pollMs = 1000).awaitCool { pauses += it }

        assertEquals(listOf(true, false), pauses)
        assertEquals(3, reads)
        assertEquals(2000L, testScheduler.currentTime)
    }
}
