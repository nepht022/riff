package com.rodrigomachado.riff.feature.calibration

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickTrackTest {
    @Test
    fun twentyFourClicksSixHundredMillisecondsApartAfterAShortPause() {
        val frames = ClickTrack.clickFrames(sampleRate = 1_000)

        assertEquals(24, frames.size)
        assertEquals(500L, frames[0])
        assertEquals(1_100L, frames[1])
        assertEquals(500L + 23 * 600L, frames.last())
    }

    @Test
    fun theRenderedTrackHasSoundOnlyAtTheClicks() {
        val rate = 44_100
        val pcm = ClickTrack.render(rate)
        val first = ClickTrack.clickFrames(rate)[0].toInt()

        assertEquals(0f, pcm[(first - 10) * 2], 0f)
        assertTrue((0 until 200).any { kotlin.math.abs(pcm[(first + it) * 2]) > 0.1f })
        assertTrue(pcm.all { it in -1f..1f })
    }

    @Test
    fun clickInstantsComeFromWhereTheOutputWas() {
        val timeline = ClickTimeline(sampleRate = 1_000, clickFrames = longArrayOf(500, 1_100))

        timeline.observe(nowMs = 10_000, playedFrames = 200) // o clique 0 sai em 10.300
        timeline.observe(nowMs = 10_400, playedFrames = 600) // o clique 1 sai em 10.900
        timeline.observe(nowMs = 10_402, playedFrames = 600) // saída parada (buffer): leitura ignorada

        assertArrayEquals(longArrayOf(10_300, 10_900), timeline.clickTimesMs().toLongArray())
    }

    @Test
    fun withoutObservationsThereAreNoInstants() {
        assertEquals(emptyList<Long>(), ClickTimeline(1_000, longArrayOf(500)).clickTimesMs())
    }
}
