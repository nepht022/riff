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

        assertEquals(190L, presented.update(head = 900, stampFrame = 90, stampNs = 100 * ms, nowNs = 200 * ms))
        assertEquals(900L, presented.update(head = 900, stampFrame = 90, stampNs = 100 * ms, nowNs = 2_000 * ms))
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
        presented.update(head = 700, stampFrame = 500, stampNs = 500 * ms, nowNs = 600 * ms)
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

    @Test
    fun rightAfterResumingAnEarlyTimestampCannotRunAheadOfTheTimeSinceResuming() {
        val presented = PresentedFrames(rate)
        presented.started(nowNs = 0)
        presented.update(head = 30_800, stampFrame = 30_580, stampNs = 30_580 * ms, nowNs = 30_580 * ms)
        presented.paused()

        presented.started(nowNs = 40_000 * ms)
        // a primeira leitura depois de continuar diz 345 ms à frente, só 30 ms depois: impossível
        val early = presented.update(head = 31_200, stampFrame = 30_925, stampNs = 40_030 * ms, nowNs = 40_030 * ms)

        assertEquals(30_610L, early)
        val later = presented.update(head = 31_600, stampFrame = 31_080, stampNs = 40_500 * ms, nowNs = 40_500 * ms)
        assertEquals(31_080L, later)
    }

    @Test
    fun aTimestampThatHasNotPresentedAnyFrameYetMeansTheSoundIsNotOut() {
        val presented = PresentedFrames(rate)
        presented.started(nowNs = 0)

        // saída nova: o timestamp já é "de agora", mas ainda no quadro 0 (o som não chegou no alto-falante)
        assertEquals(0L, presented.update(head = 400, stampFrame = 0, stampNs = 20 * ms, nowNs = 200 * ms))
        assertFalse(presented.isPresenting)

        assertEquals(30L, presented.update(head = 400, stampFrame = 10, stampNs = 230 * ms, nowNs = 250 * ms))
        assertTrue(presented.isPresenting)
    }
}
