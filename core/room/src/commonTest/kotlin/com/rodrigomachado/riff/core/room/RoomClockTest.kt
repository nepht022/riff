package com.rodrigomachado.riff.core.room

import org.junit.Assert.assertEquals
import org.junit.Test

class RoomClockTest {
    private var now = 1_000L

    @Test
    fun serverNowAddsTheOffsetToTheDeviceClock() {
        val clock = RoomClock(offsetMs = 300_000, clock = { now })

        assertEquals(301_000L, clock.serverNow())
        now += 500
        assertEquals(301_500L, clock.serverNow())
    }

    @Test
    fun toLocalTakesTheOffsetOut() {
        val clock = RoomClock(offsetMs = -2_000, clock = { now })

        assertEquals(12_000L, clock.toLocal(10_000))
        assertEquals(now, clock.toLocal(clock.serverNow()))
    }
}
