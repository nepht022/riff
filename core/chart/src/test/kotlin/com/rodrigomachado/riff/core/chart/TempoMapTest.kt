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
}
