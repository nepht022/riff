package com.rodrigomachado.riff.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MissSoundPickerTest {
    private val picker = MissSoundPicker(count = 6, random = Random(42))

    @Test
    fun neverRepeatsTheSameVariationTwiceInARowAndUsesThemAll() {
        val picks = (0 until 200).map { checkNotNull(picker.pick(nowMs = it * 300L)).index }

        picks.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
        assertEquals((0 until 6).toSet(), picks.toSet())
    }

    @Test
    fun missesCloserThanAQuarterSecondDoNotPlayAnotherSound() {
        assertNotNull(picker.pick(nowMs = 1_000))

        assertNull(picker.pick(nowMs = 1_100))
        assertNull(picker.pick(nowMs = 1_249))
        assertNotNull(picker.pick(nowMs = 1_250))
    }

    @Test
    fun aSkippedMissDoesNotPushTheNextSoundAway() {
        picker.pick(nowMs = 0)
        picker.pick(nowMs = 200) // ignorado

        assertNotNull(picker.pick(nowMs = 260))
    }

    @Test
    fun strengthVariesLikeARealHand() {
        val gains = (0 until 100).map { checkNotNull(picker.pick(nowMs = it * 300L)).gain }

        assertTrue(gains.all { it in 0.75f..1f })
        assertTrue(gains.toSet().size > 10)
    }

    @Test
    fun aSingleVariationIsAlwaysTheOneChosen() {
        val single = MissSoundPicker(count = 1, random = Random(1))

        assertEquals(0, single.pick(0)?.index)
        assertEquals(0, single.pick(300)?.index)
    }
}
