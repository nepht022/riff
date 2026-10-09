package com.rodrigomachado.riff.feature.calibration

import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationSessionTest {
    private val clicks = List(CalibrationSession.LEAD_IN + CalibrationSession.MEASURED) { 1_000L + it * 600L }
    private val measured = clicks.drop(CalibrationSession.LEAD_IN)

    @Test
    fun tapsFortyMillisecondsLateMeasureForty() {
        val taps = measured.map { it + 40 }

        assertEquals(CalibrationResult.Measured(40), CalibrationSession.result(clicks, taps))
    }

    @Test
    fun tapsOutOfRhythmAreDiscarded() {
        val taps = measured.mapIndexed { i, click -> if (i < 3) click + 290 else click + 35 }

        assertEquals(CalibrationResult.Measured(35), CalibrationSession.result(clicks, taps))
    }

    @Test
    fun fewerThanTwelveGoodTapsIsUnstable() {
        val taps = measured.take(11).map { it + 40 }

        assertEquals(CalibrationResult.Unstable, CalibrationSession.result(clicks, taps))
    }

    @Test
    fun missedClicksDoNotBreakTheMeasurement() {
        val taps = measured.filterIndexed { i, _ -> i % 4 != 0 }.map { it + 50 }

        assertEquals(CalibrationResult.Measured(50), CalibrationSession.result(clicks, taps))
    }

    @Test
    fun twoTapsOnTheSameClickKeepTheClosest() {
        val taps = measured.map { it + 40 } + measured.take(5).map { it + 200 }

        assertEquals(CalibrationResult.Measured(40), CalibrationSession.result(clicks, taps.sorted()))
    }

    @Test
    fun tapsOnTheLeadInClicksDoNotCount() {
        val taps = clicks.take(CalibrationSession.LEAD_IN).map { it - 100 } + measured.map { it + 40 }

        assertEquals(CalibrationResult.Measured(40), CalibrationSession.result(clicks, taps))
    }

    @Test
    fun tappingBeforeTheClickGivesANegativeLatency() {
        val taps = measured.map { it - 15 }

        assertEquals(CalibrationResult.Measured(-15), CalibrationSession.result(clicks, taps))
    }
}
