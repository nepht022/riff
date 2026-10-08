package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.chart.TempoMap.TempoChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TempoMapTest {
    @Test
    fun defaultsTo120BpmWithoutTempoChanges() {
        val map = TempoMap(resolution = 192, changes = emptyList())
        assertEquals(500.0, map.tickToMs(192), 0.001)
    }

    @Test
    fun appliesTempoChangeMidSong() {
        val map = TempoMap(192, listOf(TempoChange(0, 500_000), TempoChange(384, 1_000_000)))
        assertEquals(1000.0, map.tickToMs(384), 0.001)
        assertEquals(2000.0, map.tickToMs(576), 0.001)
    }

    @Test
    fun assumes120BpmBeforeFirstChangeWhenItIsNotAtZero() {
        val map = TempoMap(192, listOf(TempoChange(192, 1_000_000)))
        assertEquals(500.0, map.tickToMs(192), 0.001)
        assertEquals(1500.0, map.tickToMs(384), 0.001)
    }

    @Test
    fun convertsMilliBpmToMicrosPerQuarter() {
        assertEquals(500_000L, TempoMap.microsPerQuarterFromMilliBpm(120_000))
        assertEquals(400_000L, TempoMap.microsPerQuarterFromMilliBpm(150_000))
    }

    @Test
    fun matchesStepByStepSumWithHundredsOfTempoChanges() {
        // 500 mudanças a cada 192 ticks, alternando 120 e 60 BPM: cada trecho dura 500 ms ou 1000 ms.
        val changes = List(500) { i -> TempoChange(i * 192L, if (i % 2 == 0) 500_000 else 1_000_000) }
        val map = TempoMap(192, changes)
        var expectedStart = 0.0
        for (i in 0 until 500) {
            val segmentMs = if (i % 2 == 0) 500.0 else 1000.0
            assertEquals(expectedStart, map.tickToMs(i * 192L), 0.001) // exatamente na fronteira
            assertEquals(expectedStart + segmentMs / 2, map.tickToMs(i * 192L + 96), 0.001) // no meio do trecho
            expectedStart += segmentMs
        }
        assertEquals(expectedStart + 500.0, map.tickToMs(500 * 192L + 96), 0.001) // depois da última mudança
    }

    @Test
    fun laterChangeWinsWhenTwoShareTheSameTick() {
        val map = TempoMap(
            192,
            listOf(TempoChange(0, 500_000), TempoChange(192, 2_000_000), TempoChange(192, 1_000_000)),
        )
        assertEquals(1500.0, map.tickToMs(384), 0.001)
    }
}
