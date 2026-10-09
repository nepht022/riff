package com.rodrigomachado.riff.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentedFramesTest {
    private val rate = 1_000 // 1 quadro por ms deixa as contas legíveis
    private val ms = 1_000_000L

    @Test
    fun aFreshTimestampIsExtrapolatedAndLimitedToTheHead() {
        val presented = PresentedFrames(rate)
        presented.started(nowNs = 0)

        assertEquals(500L, presented.update(head = 900, stampFrame = 400, stampNs = 100 * ms, nowNs = 200 * ms))
        assertEquals(900L, presented.update(head = 900, stampFrame = 400, stampNs = 100 * ms, nowNs = 2_000 * ms))
        assertTrue(presented.isPresenting)
    }

    @Test
    fun afterResumingAStaleTimestampDoesNotJumpToTheHead() {
        val presented = PresentedFrames(rate)
        presented.started(nowNs = 0)
        presented.update(head = 1_200, stampFrame = 1_000, stampNs = 1_000 * ms, nowNs = 1_000 * ms)
        presented.paused()

        presented.started(nowNs = 5_000 * ms) // continuou depois de 4 s pausado
        val stale = presented.update(head = 1_300, stampFrame = 1_000, stampNs = 1_000 * ms, nowNs = 5_050 * ms)

        assertEquals(1_000L, stale) // segura onde parou, em vez de pular para a cabeça (1.300)
        assertFalse(presented.isPresenting)
        val fresh = presented.update(head = 1_300, stampFrame = 1_010, stampNs = 5_100 * ms, nowNs = 5_120 * ms)
        assertEquals(1_030L, fresh)
        assertTrue(presented.isPresenting)
    }

    @Test
    fun whilePausedThePositionHolds() {
        val presented = PresentedFrames(rate)
        presented.started(nowNs = 0)
        presented.update(head = 700, stampFrame = 500, stampNs = 0, nowNs = 100 * ms)
        presented.paused()

        assertEquals(600L, presented.frames)
        assertFalse(presented.isPresenting)
    }

    @Test
    fun withoutTimestampsTheHeadIsUsedAfterAWhile() {
        val presented = PresentedFrames(rate)
        presented.started(nowNs = 0)

        assertEquals(0L, presented.update(head = 300, stampFrame = null, stampNs = null, nowNs = 100 * ms))
        assertEquals(900L, presented.update(head = 900, stampFrame = null, stampNs = null, nowNs = 1_100 * ms))
        assertTrue(presented.isPresenting)
    }
}
