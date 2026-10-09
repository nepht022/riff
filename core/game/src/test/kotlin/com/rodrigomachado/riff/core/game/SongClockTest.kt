package com.rodrigomachado.riff.core.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SongClockTest {
    private class FakeTime(var now: Long = 0) : TimeSource {
        override fun nowMs() = now
    }

    private val time = FakeTime()
    private val clock = SongClock(time)

    @Test
    fun startsPausedAtZero() {
        time.now = 5_000
        assertEquals(0L, clock.positionMs())
        assertEquals(true, clock.isPaused)
    }

    @Test
    fun extrapolatesWhilePlaying() {
        time.now = 5_000
        clock.sync(positionMs = 1_000, isPaused = false, capturedAtMs = 5_000)
        time.now = 5_100
        assertEquals(1_100L, clock.positionMs())
    }

    @Test
    fun accountsForCaptureInThePast() {
        time.now = 5_000
        clock.sync(positionMs = 1_000, isPaused = false, capturedAtMs = 4_950)
        assertEquals(1_050L, clock.positionMs())
    }

    @Test
    fun freezesWhilePaused() {
        clock.sync(positionMs = 1_000, isPaused = true, capturedAtMs = 0)
        time.now = 3_000
        assertEquals(1_000L, clock.positionMs())
    }

    @Test
    fun appliesOffset() {
        val shifted = SongClock(time, offsetMs = -200)
        shifted.sync(positionMs = 1_000, isPaused = false, capturedAtMs = 0)
        assertEquals(800L, shifted.positionMs())
    }

    @Test
    fun correctsSmallDriftGradually() {
        clock.sync(positionMs = 1_000, isPaused = false, capturedAtMs = 0)
        time.now = 1_000
        assertEquals(2_000L, clock.positionMs())

        clock.sync(positionMs = 2_040, isPaused = false, capturedAtMs = 1_000) // o player está 40 ms à frente
        assertEquals(2_000L, clock.positionMs()) // sem salto
        time.now = 1_100
        assertEquals(2_130L, clock.positionMs()) // corrigiu 30 ms dos 40 em 100 ms
        time.now = 1_200
        assertEquals(2_240L, clock.positionMs()) // convergiu
    }

    @Test
    fun appliesLargeJumpImmediately() {
        clock.sync(positionMs = 1_000, isPaused = false, capturedAtMs = 0)
        time.now = 1_000
        clock.sync(positionMs = 30_000, isPaused = false, capturedAtMs = 1_000)
        assertEquals(30_000L, clock.positionMs())
        clock.sync(positionMs = 5_000, isPaused = false, capturedAtMs = 1_000) // voltou a barra de progresso
        assertEquals(5_000L, clock.positionMs())
    }

    @Test
    fun pauseToggleAppliesImmediately() {
        clock.sync(positionMs = 1_000, isPaused = false, capturedAtMs = 0)
        time.now = 500
        clock.sync(positionMs = 1_520, isPaused = true, capturedAtMs = 500)
        time.now = 2_000
        assertEquals(1_520L, clock.positionMs())
        assertEquals(true, clock.isPaused)
    }

    @Test
    fun newPositionWhilePausedAppliesImmediatelyAndStaysFrozen() {
        clock.sync(positionMs = 1_000, isPaused = true, capturedAtMs = 0)
        time.now = 500
        clock.sync(positionMs = 1_040, isPaused = true, capturedAtMs = 500) // desvio pequeno, ainda pausado
        assertEquals(1_040L, clock.positionMs())
        time.now = 2_000
        assertEquals(1_040L, clock.positionMs())
    }

    @Test
    fun changingTheOffsetMovesThePositionAtOnce() {
        time.now = 5_000
        clock.sync(positionMs = 1_000, isPaused = true, capturedAtMs = 5_000)

        clock.offsetMs = -40

        assertEquals(960L, clock.positionMs())
    }
}
